
package org.camunda.tngp.broker.system;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.camunda.tngp.util.FileUtil;

public class GlobalConfiguration extends ComponentConfiguration
{
    public static final String DEFAULT_DATA_PATH = "./tngp-data";
    public String directory = null;
    public Boolean useTempDirectory = null;
    private Boolean useCurrentPath = false;

    public void init()
    {
        if (useTempDirectory != null && this.directory != null)
        {
            throw new RuntimeException("'directory' and 'useTempDirectory' cannot exist at the same time, please delete one");
        }

        if (useTempDirectory == null && this.directory == null)
        {
            System.out.println("No specific temporary nor data path, will use '" + DEFAULT_DATA_PATH + "' to store data.");
            useCurrentPath = true;
            this.directory = DEFAULT_DATA_PATH;
        }

        if (this.useTempDirectory != null && this.useTempDirectory)
        {
            try
            {
                this.directory = Files.createTempDirectory("tngp-temp-").toString();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            System.out.println("Using Temp Dir: " + this.directory);
        }


        this.directory = FileUtil.getCanonicalDirectoryPath(this.directory);
    }

    public String getGlobalDataDirectory()
    {
        return this.directory;
    }

    public Boolean getGlobalUseTemp()
    {
        return this.useTempDirectory;
    }

    public void cleanTempFolder()
    {
        if (this.useTempDirectory != null && this.useTempDirectory && new File(this.directory).exists())
        {
            FileUtil.deleteFolder(this.directory);
        }
        if (useCurrentPath && new File(this.directory).exists())
        {
            FileUtil.deleteFolder(this.directory);
        }
    }

}
