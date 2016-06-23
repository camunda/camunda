package org.camunda.tngp.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import static java.nio.file.FileVisitResult.CONTINUE;

import uk.co.real_logic.agrona.LangUtil;

public class FileUtil
{

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

}
