package io.innoasis.y1syncer.storage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

public class PlaylistWriter {
    public File writeM3u8(File folder, String playlistName, List<String> relativeTracks) throws IOException {
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File out = new File(folder, playlistName + ".m3u8");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
        try {
            writer.write("#EXTM3U\n");
            for (String track : relativeTracks) {
                writer.write(track);
                writer.write('\n');
            }
        } finally {
            writer.close();
        }
        return out;
    }
}
