
package org.camunda.tngp.broker.system;

import java.io.IOException;
import java.nio.file.Files;

public class GlobalConfiguration extends ComponentConfiguration
{

    public String globalDataDirectory = null;
    public Boolean globalUseTemp = null;


    public void init()
    {
        if (globalUseTemp == null)
        {
            if (this.globalDataDirectory == null)
            {
                System.out.println("WARNING! there is no 'globalUseTemp' nor 'globalDataDirectory' in TOML configuration file, will automatically set 'globalUseTemp = true'");
                this.globalUseTemp = true;
            }
            else
            {
                return;
            }
        }
        if (this.globalUseTemp)
        {
            try
            {
                this.globalDataDirectory = Files.createTempDirectory("tngp-temp-").toString() + "/";
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            System.out.println("Using Temp Dir: " + this.globalDataDirectory);
        }
    }

    public String getGlobalDataDirectory()
    {
        return this.globalDataDirectory;
    }
    public Boolean getGlobalUseTemp()
    {
        return this.globalUseTemp;
    }


}
