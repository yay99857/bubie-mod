package com.bubie.client.trailer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Screenshot;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * DEV ONLY: pipes frames of the game window into ffmpeg (raw RGBA in, H.264 out), one video per shot. A writer thread
 * feeds ffmpeg so the render thread only copies pixels; a small queue keeps memory use flat (each 1080p frame is 8 MB).
 */
final class Recorder {
    private static final Logger LOG = LogUtils.getLogger();
    private static final byte[] END = new byte[0];

    private final Process ffmpeg;
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(6);
    private final Thread writer;
    private final int width, height;
    private int frames;
    private volatile boolean failed;

    Recorder(File out, int width, int height, int fps) throws IOException {
        this.width = width;
        this.height = height;
        out.getParentFile().mkdirs();
        ffmpeg = new ProcessBuilder(List.of(System.getProperty("bubie.ffmpeg", "ffmpeg"), "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", Integer.toString(fps), "-i", "-",
                "-vf", "crop=trunc(iw/2)*2:trunc(ih/2)*2", "-c:v", "libx264", "-preset", "medium", "-crf", "14", "-pix_fmt", "yuv420p", out.getAbsolutePath()))
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start();
        OutputStream pipe = ffmpeg.getOutputStream();
        writer = new Thread(() -> {
            try (pipe) {
                while (true) {
                    byte[] frame = queue.take();
                    if (frame == END) break;
                    pipe.write(frame);
                }
            } catch (IOException | InterruptedException e) {
                failed = true;
                queue.clear();
                LOG.error("Trailer: ffmpeg pipe failed", e);
            }
        }, "Bubie trailer writer");
        writer.start();
    }

    /** Copies the finished frame (after shaders and everything) into the video. */
    void capture(RenderTarget target) {
        if (failed || target.width != width || target.height != height) return; // ffmpeg died, or the window was resized
        try (NativeImage image = Screenshot.takeScreenshot(target)) {
            int[] pixels = image.getPixelsRGBA(); // 0xAABBGGRR: little-endian bytes are R, G, B, A
            byte[] bytes = new byte[pixels.length * 4];
            for (int i = 0, b = 0; i < pixels.length; i++) {
                int p = pixels[i];
                bytes[b++] = (byte) p;
                bytes[b++] = (byte) (p >> 8);
                bytes[b++] = (byte) (p >> 16);
                bytes[b++] = (byte) 0xFF;
            }
            // waits for ffmpeg (keeps memory flat), but never forever: a dead encoder must not freeze the game
            if (queue.offer(bytes, 30, TimeUnit.SECONDS)) frames++;
            else failed = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    int frames() {
        return frames;
    }

    void finish() {
        try {
            if (!failed) queue.offer(END, 30, TimeUnit.SECONDS);
            writer.join(60000);
            ffmpeg.waitFor(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
