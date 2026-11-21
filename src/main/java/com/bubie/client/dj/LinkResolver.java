package com.bubie.client.dj;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the share link of a file or music site into a link to the audio itself, so players can paste what the site
 * gives them: Dropbox and Google Drive share links, GitHub file pages, and Audius track pages (an open music platform
 * whose public API streams MP3). Anything else is played as it is. Runs on the DJ's download thread, never the game's.
 */
public final class LinkResolver {
    private static final String AUDIUS_API = "https://api.audius.co/v1/";
    private static final String APP_NAME = "bubie-dj";
    private static final Pattern DRIVE_FILE = Pattern.compile("/file/d/([A-Za-z0-9_-]+)");
    private static final Pattern ID_PARAM = Pattern.compile("(?:^|&)id=([A-Za-z0-9_-]+)");

    private LinkResolver() {
    }

    /** The direct audio link for a pasted link; may ask the Audius API. */
    public static String resolve(String url) throws IOException {
        URI uri = URI.create(url);
        if (host(uri).equals("audius.co")) return audius(uri);
        return rewrite(url);
    }

    /** The rewrites that need no network: Dropbox, Google Drive, GitHub. Other links come back unchanged. */
    public static String rewrite(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return url;
        }
        String host = host(uri), path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        switch (host) {
            case "dropbox.com" -> {
                // dl=0 opens the preview page, dl=1 sends the file
                String q = query.replaceAll("(^|&)dl=[^&]*", "").replaceAll("^&", "");
                return "https://www.dropbox.com" + path + "?" + (q.isEmpty() ? "" : q + "&") + "dl=1";
            }
            case "drive.google.com" -> {
                Matcher m = DRIVE_FILE.matcher(path);
                String id = m.find() ? m.group(1) : null;
                if (id == null) {
                    Matcher p = ID_PARAM.matcher(query);
                    if (p.find()) id = p.group(1);
                }
                // confirm=t skips the "can't scan this file for viruses" page of big files
                if (id != null) return "https://drive.usercontent.google.com/download?id=" + id + "&export=download&confirm=t";
            }
            case "github.com" -> {
                // github.com/user/repo/blob/branch/file.ogg is a web page; /raw/ is the file
                if (path.matches("/[^/]+/[^/]+/blob/.+")) return "https://github.com" + path.replaceFirst("/blob/", "/raw/");
            }
            default -> {
            }
        }
        return url;
    }

    /** audius.co/artist/track → the track's id from the API → its MP3 stream. */
    private static String audius(URI page) throws IOException {
        String[] parts = page.getPath() == null ? new String[0] : page.getPath().replaceAll("^/+|/+$", "").split("/");
        if (parts.length != 2) throw new IOException("only Audius track links play (not albums, playlists or profiles)");
        String pageUrl = "https://audius.co/" + parts[0] + "/" + parts[1];
        JsonObject json = getJson(AUDIUS_API + "resolve?url=" + URLEncoder.encode(pageUrl, StandardCharsets.UTF_8)
                + "&app_name=" + APP_NAME);
        JsonElement data = json.get("data");
        if (data == null || !data.isJsonObject() || !data.getAsJsonObject().has("id"))
            throw new IOException("Audius doesn't know this track: " + pageUrl);
        JsonObject track = data.getAsJsonObject();
        if (track.has("is_streamable") && !track.get("is_streamable").getAsBoolean())
            throw new IOException("this Audius track can't be streamed");
        String id = track.get("id").getAsString();
        if (!id.matches("[A-Za-z0-9]+")) throw new IOException("odd Audius id: " + id);
        return AUDIUS_API + "tracks/" + id + "/stream?app_name=" + APP_NAME;
    }

    private static JsonObject getJson(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setInstanceFollowRedirects(true); // stays on https://api.audius.co
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "Bubie-DJ (Minecraft mod)");
        c.setRequestProperty("Accept", "application/json");
        try {
            int code = c.getResponseCode();
            if (code == 404) throw new IOException("Audius doesn't know this track");
            if (code != 200) throw new IOException("Audius answered HTTP " + code);
            try (Reader in = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(in);
                if (!root.isJsonObject()) throw new IOException("odd answer from Audius");
                return root.getAsJsonObject();
            }
        } catch (RuntimeException e) { // Gson's parse errors
            throw new IOException("odd answer from Audius", e);
        } finally {
            c.disconnect();
        }
    }

    private static String host(URI uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }
}
