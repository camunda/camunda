package io.zeebe.broker.system;

import static io.zeebe.util.FileUtil.createTempDirectory;
import static io.zeebe.util.FileUtil.getCanonicalPath;

import java.io.File;

import io.zeebe.broker.Loggers;
import org.slf4j.Logger;

public class GlobalConfiguration extends DirectoryConfiguration
{
    public static final Logger LOG = Loggers.SYSTEM_LOGGER;

    public static final String GLOBAL_DIRECTORY_DEFAULT = "./data";
    public static final String GLOBAL_DIRECTORY_TEMP = "zeebe-data-";

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

        LOG.info("Using data directory: {}", directory);
    }

    public boolean isTempDirectory()
    {
        return useTempDirectory;
    }

}
