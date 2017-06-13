package org.camunda.tngp.broker.system;

import static org.camunda.tngp.util.FileUtil.*;

import java.io.File;

public class GlobalConfiguration extends DirectoryConfiguration
{
    public static final String GLOBAL_DIRECTORY_DEFAULT = "./data";
    public static final String GLOBAL_DIRECTORY_TEMP = "tngp-data-";

    public boolean useTempDirectory;

    public void init()
    {
        if (directory == null || directory.isEmpty())
        {
            if (!useTempDirectory)
            {
                directory = GLOBAL_DIRECTORY_DEFAULT;
            }
            else
            {
                final File tmp = createTempDirectory(GLOBAL_DIRECTORY_TEMP);
                directory = tmp.toString();

            }
        }
        else if (useTempDirectory)
        {
            throw new RuntimeException("Can't use attribute 'directory' and element 'useTempDirectory' together as global configuration, only use one.");
        }

        directory = getCanonicalPath(directory);

        System.out.println(String.format("Using directory: %s", directory));
    }

    public boolean isTempDirectory()
    {
        return useTempDirectory;
    }

}
