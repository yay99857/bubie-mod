package com.bubie.client.dj;

import com.mojang.logging.LogUtils;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * An audio link (.ogg or .mp3) streamed from the internet. A thread of its own downloads and decodes it into a queue
 * of PCM chunks, so a slow connection never blocks the sound engine: while the queue is empty the engine gets a
 * moment of silence instead of an end. The stream is ready once about a second is buffered.
 */
public final class UrlAudioStream implements AudioStream {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int CONNECT_TIMEOUT = 8000, READ_TIMEOUT = 15000, MAX_REDIRECTS = 5;
    private static final long MAX_BYTES = 200L * 1024 * 1024;

    /** One decoded format: OGG (Minecraft's own decoder) or MP3 (JLayer). */
    private interface Source {
        AudioFormat format();

        /** The next PCM chunk (16-bit little-endian), or null at the end. */
        @Nullable
        ByteBuffer next() throws IOException;

        void close() throws IOException;
    }

    private final Source source;
    private final AudioFormat format;
    private final BlockingQueue<ByteBuffer> queue = new LinkedBlockingQueue<>(128);
    private volatile boolean finished, closed;
    @Nullable
    private volatile IOException failure;

    public static CompletableFuture<AudioStream> open(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UrlAudioStream stream = new UrlAudioStream(url);
                stream.prebuffer();
                return stream;
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, Util.nonCriticalIoPool());
    }

    private UrlAudioStream(String url) throws IOException {
        InputStream in = new BufferedInputStream(new LimitedInputStream(connect(url), MAX_BYTES), 64 * 1024);
        in.mark(16);
        byte[] head = in.readNBytes(4);
        in.reset();
        source = head.length >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S'
                ? ogg(in) : mp3(in);
        format = source.format();
        LOG.info("Bubie DJ: streaming {} ({} Hz, {} channels)", url, (int) format.getSampleRate(), format.getChannels());
        Thread thread = new Thread(this::pump, "Bubie DJ stream");
        thread.setDaemon(true);
        thread.start();
    }

    private static InputStream connect(String url) throws IOException {
        URI uri = URI.create(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) throw new IOException("not http: " + uri);
            HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
            c.setInstanceFollowRedirects(false); // followed by hand, so an https link can't be sent to another scheme
            c.setConnectTimeout(CONNECT_TIMEOUT);
            c.setReadTimeout(READ_TIMEOUT);
            c.setRequestProperty("User-Agent", "Bubie-DJ (Minecraft mod)");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400 && c.getHeaderField("Location") != null) {
                uri = uri.resolve(c.getHeaderField("Location"));
                c.disconnect();
                continue;
            }
            if (code != 200) throw new IOException("HTTP " + code + " for " + uri);
            return c.getInputStream();
        }
        throw new IOException("too many redirects: " + url);
    }

    private static Source ogg(InputStream in) throws IOException {
        JOrbisAudioStream jorbis = new JOrbisAudioStream(in);
        return new Source() {
            @Override
            public AudioFormat format() {
                return jorbis.getFormat();
            }

            @Override
            public ByteBuffer next() throws IOException {
                ByteBuffer chunk = jorbis.read(16384);
                return chunk.hasRemaining() ? chunk : null;
            }

            @Override
            public void close() throws IOException {
                jorbis.close();
            }
        };
    }

    private static Source mp3(InputStream in) throws IOException {
        Bitstream bits = new Bitstream(in);
        Decoder decoder = new Decoder();
        // decode the first frame now: it gives the sample rate and channels
        ByteBuffer first;
        Header header;
        try {
            header = bits.readFrame();
            if (header == null) throw new IOException("not an mp3 or ogg file");
            first = pcm((SampleBuffer) decoder.decodeFrame(header, bits));
            bits.closeFrame();
        } catch (BitstreamException | DecoderException e) {
            throw new IOException("not an mp3 or ogg file", e);
        }
        AudioFormat format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
        return new Source() {
            private ByteBuffer pending = first;

            @Override
            public AudioFormat format() {
                return format;
            }

            @Override
            public ByteBuffer next() throws IOException {
                if (pending != null) {
                    ByteBuffer out = pending;
                    pending = null;
                    return out;
                }
                try {
                    Header h = bits.readFrame();
                    if (h == null) return null;
                    ByteBuffer out = pcm((SampleBuffer) decoder.decodeFrame(h, bits));
                    bits.closeFrame();
                    return out;
                } catch (BitstreamException | DecoderException e) {
                    throw new IOException("bad mp3 frame", e);
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    bits.close();
                } catch (BitstreamException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    private static ByteBuffer pcm(SampleBuffer samples) {
        short[] data = samples.getBuffer();
        int n = samples.getBufferLength();
        ByteBuffer out = BufferUtils.createByteBuffer(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < n; i++) out.putShort(data[i]);
        out.flip();
        return out;
    }

    private void pump() {
        try {
            ByteBuffer chunk;
            while (!closed && (chunk = source.next()) != null) {
                while (!closed && !queue.offer(chunk, 200, TimeUnit.MILLISECONDS)) {
                    // the engine plays slower than we decode: wait
                }
            }
        } catch (IOException e) {
            failure = e;
            LOG.warn("Bubie DJ: stream failed: {}", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (failure == null) LOG.info("Bubie DJ: stream decoded to the end");
            finished = true;
            try {
                source.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
    }

    /** Waits until about a second is decoded (or the file ended); fails if nothing came at all. */
    private void prebuffer() throws IOException {
        long bytesPerSecond = (long) (format.getSampleRate() * format.getChannels() * 2);
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            long buffered = 0;
            for (ByteBuffer b : queue) buffered += b.remaining();
            if (buffered >= bytesPerSecond || finished) break;
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (queue.isEmpty() && failure != null) throw failure;
        if (queue.isEmpty() && finished) throw new IOException("empty audio");
    }

    /** True once everything was decoded and handed to the engine (or the download failed). */
    public boolean drained() {
        return finished && queue.isEmpty();
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        ByteBuffer chunk = queue.poll();
        if (chunk != null) return chunk;
        if (finished) return null; // the end: the engine lets the queued buffers finish and stops
        // waiting for the network: a tenth of a second of silence keeps the sound alive
        int bytes = (int) (format.getSampleRate() / 10) * format.getChannels() * 2;
        ByteBuffer silence = BufferUtils.createByteBuffer(bytes);
        silence.limit(bytes);
        return silence;
    }

    @Override
    public void close() {
        closed = true;
        queue.clear();
    }

    /** Stops reading after a size limit, so a huge or endless link can't fill the disk or memory. */
    private static final class LimitedInputStream extends java.io.FilterInputStream {
        private long left;

        LimitedInputStream(InputStream in, long limit) {
            super(in);
            this.left = limit;
        }

        @Override
        public int read() throws IOException {
            if (left <= 0) return -1;
            int b = super.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(buf, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }
}
