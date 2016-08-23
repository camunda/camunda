package org.camunda.tngp.log.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;

import org.agrona.LangUtil;

public class FileChannelUtil
{

    public static long getAvailableSpace(File logLocation)
    {
        long usableSpace = -1;

        try
        {
            final FileStore fileStore = Files.getFileStore(logLocation.toPath());
            usableSpace = fileStore.getUsableSpace();
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return usableSpace;
    }

    @SuppressWarnings("resource")
    public static FileChannel openChannel(String filename, boolean create)
    {
        FileChannel fileChannel = null;
        try
        {
            final File file = new File(filename);
            if (!file.exists())
            {
                if (create)
                {
                    file.createNewFile();
                }
                else
                {
                    return null;
                }

            }

            final RandomAccessFile raf = new RandomAccessFile(file, "rw");
            fileChannel = raf.getChannel();
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return fileChannel;
    }

}
