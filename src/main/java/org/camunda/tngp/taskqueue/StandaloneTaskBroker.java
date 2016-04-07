package org.camunda.tngp.taskqueue;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import uk.co.real_logic.agrona.LangUtil;

public class StandaloneTaskBroker
{

    public static void main(String[] args)
    {
        Properties properties = new Properties();

        File propertiesFile = new File("tngp.properties");

        if(propertiesFile.exists())
        {
            System.out.println("Using Properties file "+propertiesFile.getAbsolutePath());
            try
            {
                properties.load(new FileInputStream(propertiesFile));
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
        else
        {
            System.out.println("Using default properties");
        }

        properties.setProperty(TaskBrokerProperties.BROKER_THREAD_COUNT, "2");

        TaskBroker taskBroker = new TaskBroker(properties);
    }

}
