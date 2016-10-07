package org.camunda.tngp.client;

import java.util.Properties;

public class Client
{

    public static void main(final String[] args)
    {
        final Properties properties = new Properties();
        properties.setProperty("tngp.client.broker.contactPoint", "localhost:51015");
        final TngpClient client = TngpClient.create(properties);
        client.connect();

        System.out.println("start " +  System.currentTimeMillis());
        for (int i = 0; i < 100000; i++)
        {
            client.tasks().create().taskQueueId(0).taskType("foo" + i).execute();
        }
        System.out.println("finished " +  System.currentTimeMillis());

    }

}
