package io.zeebe.broker.system;

import java.io.File;

import io.zeebe.util.FileUtil;

public abstract class DirectoryConfiguration extends ComponentConfiguration
{
    private static final String SUB_DIRECTORY_NAME_PATTERN = "%s" + File.separator + "%s";

    public String directory;

    @Override
    public void applyGlobalConfiguration(GlobalConfiguration globalConfiguration)
    {
        String localDirectory = directory;

        if (localDirectory == null || localDirectory.isEmpty())
        {
            final String globalDirectory = globalConfiguration.directory;
            final String subDirectory = componentDirectoryName();

            if (subDirectory != null && !subDirectory.isEmpty())
            {
                localDirectory = String.format(SUB_DIRECTORY_NAME_PATTERN, globalDirectory, subDirectory);
            }
            else
            {
                localDirectory = globalDirectory;
            }
        }

        directory = FileUtil.getCanonicalPath(localDirectory);
    }

    protected String componentDirectoryName()
    {
        return null;
    }

    public String getDirectory()
    {
        return directory;
    }


}
