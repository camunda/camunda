package io.zeebe.util;

import static java.nio.file.FileVisitResult.*;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.CopyOption;
import java.nio.file.FileStore;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.agrona.LangUtil;

public class FileUtil
{

    public static void closeSilently(Closeable out)
    {
        if (out != null)
        {
            try
            {
                out.close();
            }
            catch (Exception e)
            {
                // ignore
            }
        }
    }

    public static void deleteFolder(String path)
    {
        final Path directory = Paths.get(path);

        try
        {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>()
            {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
                {
                    Files.delete(file);
                    return CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
                {
                    Files.delete(dir);
                    return CONTINUE;
                }

            });
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

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
                    file.getParentFile().mkdirs();
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

    public static File createTempDirectory(String prefix)
    {
        Path path = null;

        try
        {
            path = Files.createTempDirectory(prefix);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return path.toFile();
    }

    public static void moveFile(String source, String target, CopyOption... options)
    {
        final Path sourcePath = Paths.get(source);
        final Path targetPath = Paths.get(target);

        try
        {
            Files.move(sourcePath, targetPath, options);
        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public static String getCanonicalPath(String directory)
    {
        final File file = new File(directory);
        String path = null;

        try
        {
            path = file.getCanonicalPath();

            if (!path.endsWith(File.separator))
            {
                path += File.separator;
            }

        }
        catch (Exception e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return path;
    }
}
