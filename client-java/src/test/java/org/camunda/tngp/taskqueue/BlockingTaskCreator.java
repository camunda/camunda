package org.camunda.tngp.taskqueue;

import static org.camunda.tngp.client.ClientProperties.BROKER_CONTACTPOINT;

import java.util.Properties;

import org.camunda.tngp.client.AsyncTaskService;
import org.camunda.tngp.client.TngpClient;

public class BlockingTaskCreator
{

    public static void main(final String[] args)
    {
        final Properties properties = new Properties();

        properties.put(BROKER_CONTACTPOINT, "127.0.0.1:8880");

        try (TngpClient client = TngpClient.create(properties))
        {
            client.connect();

            final AsyncTaskService asyncTaskService = client.tasks();

            final byte[] payload = new byte[512];

            final long startTime = System.currentTimeMillis();

            for (int i = 0; i < 1; i++)
            {
                asyncTaskService.create()
                    .taskType("hello")
                    .payload(payload)
                    .execute();
            }

            System.out.println("took:" + (System.currentTimeMillis() - startTime));
        }

    }

}
