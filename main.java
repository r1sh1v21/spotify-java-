import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.model_objects.specification.PlaylistTrack;
import com.wrapper.spotify.model_objects.specification.Paging;
import com.wrapper.spotify.requests.data.playlists.GetPlaylistsItemsRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import org.json.*;

public class SpotifyToYouTubeDownloader {
    private static final String SPOTIFY_CLIENT_ID = System.getenv("SPOTIFY_CLIENT_ID");
    private static final String SPOTIFY_CLIENT_SECRET = System.getenv("SPOTIFY_CLIENT_SECRET");
    private static final String YOUTUBE_API_KEY = System.getenv("YOUTUBE_API_KEY");

    private static SpotifyApi spotifyApi;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter Spotify Playlist URL: ");
        String playlistUrl = scanner.nextLine();

        try {
            String playlistId = extractPlaylistId(playlistUrl);
            initializeSpotifyApi();
            List<String> songs = getSpotifyPlaylistTracks(playlistId);

            for (String song : songs) {
                System.out.println("Searching YouTube for: " + song);
                String youtubeUrl = searchYouTube(song);
                if (youtubeUrl != null) {
                    System.out.println("Found: " + youtubeUrl);
                    downloadAudio(youtubeUrl);
                } else {
                    System.out.println("No video found for: " + song);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initializeSpotifyApi() throws Exception {
        spotifyApi = new SpotifyApi.Builder()
                .setClientId(SPOTIFY_CLIENT_ID)
                .setClientSecret(SPOTIFY_CLIENT_SECRET)
                .build();

        spotifyApi.clientCredentials().build().execute();
    }

    private static String extractPlaylistId(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1].split("\\?")[0];
    }

    private static List<String> getSpotifyPlaylistTracks(String playlistId) throws Exception {
        List<String> tracks = new ArrayList<>();
        GetPlaylistsItemsRequest request = spotifyApi.getPlaylistsItems(playlistId).limit(100).build();
        Paging<PlaylistTrack> paging = request.execute();

        while (paging != null) {
            for (PlaylistTrack item : paging.getItems()) {
                String name = item.getTrack().getName();
                String artists = Arrays.stream(item.getTrack().getArtists())
                        .map(artist -> artist.getName())
                        .collect(Collectors.joining(", "));
                tracks.add(name + " - " + artists);
            }

            if (paging.getNext() != null) {
                paging = spotifyApi.getNextPaging(paging).build().execute();
            } else {
                break;
            }
        }

        return tracks;
    }

    private static String searchYouTube(String query) throws Exception {
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=" + encodedQuery +
                "&type=video&key=" + YOUTUBE_API_KEY + "&maxResults=1";

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream())
        );
        String response = reader.lines().collect(Collectors.joining());
        JSONObject json = new JSONObject(response);

        JSONArray items = json.getJSONArray("items");
        if (items.length() > 0) {
            String videoId = items.getJSONObject(0).getJSONObject("id").getString("videoId");
            return "https://www.youtube.com/watch?v=" + videoId;
        }
        return null;
    }

    private static void downloadAudio(String url) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "yt-dlp",
                "-x", "--audio-format", "mp3",
                "-o", "downloads/%(title)s.%(ext)s",
                url
        );

        builder.redirectErrorStream(true);
        Process process = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }

        process.waitFor();
        System.out.println("Download complete!");
    }
}
