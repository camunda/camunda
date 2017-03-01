package org.camunda.tngp.taskqueue;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.cmd.CreateTaskCmd;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;

public class NonBlockingTaskCreator
{
    private static final String SAMPLE_MAX_CONCURRENT_REQUESTS = "sample.maxConcurrentRequests";
    private static final String SAMPLE_PAYLOAD_SIZE = "sample.payloadSize";
    private static final String SAMPLE_NUMBER_OF_REQUESTS = "sample.numberOfRequests";

    public static void main(String[] args)
    {
        final Properties properties = System.getProperties();

        ClientProperties.setDefaults(properties);

        properties.putIfAbsent(SAMPLE_NUMBER_OF_REQUESTS, "1000000");
        properties.putIfAbsent(SAMPLE_PAYLOAD_SIZE, "512");
        properties.putIfAbsent(SAMPLE_MAX_CONCURRENT_REQUESTS, "64");

        printProperties(properties);

        final int numOfRequets = Integer.parseInt(properties.getProperty(SAMPLE_NUMBER_OF_REQUESTS));
        final int payloadSize = Integer.parseInt(properties.getProperty(SAMPLE_PAYLOAD_SIZE));
        final int maxConcurrentRequests = Integer.parseInt(properties.getProperty(SAMPLE_MAX_CONCURRENT_REQUESTS));

        try (final TngpClient client = TngpClient.create(properties))
        {
            client.connect();

            final TransportConnectionPool connectionPool = client.getConnectionPool();
            final TaskTopicClient asyncTaskService = client.taskTopic(0);

            final String payload = "{}";

            try (final TransportConnection connection = connectionPool.openConnection())
            {
                final long time = System.currentTimeMillis();

                long tasksCreated = 0;

                final List<Future<Long>> inFlightRequests = new LinkedList<>();

                while (tasksCreated < numOfRequets)
                {

                    if (inFlightRequests.size() < maxConcurrentRequests)
                    {
                        final CreateTaskCmd cmd = asyncTaskService
                                .create()
                                .taskType("greeting")
                                .payload(payload);

                        inFlightRequests.add(cmd.executeAsync(connection));
                        tasksCreated++;
                    }

                    poll(inFlightRequests);
                }

                awaitAll(inFlightRequests);

                System.out.println("Took: " + (System.currentTimeMillis() - time));
            }

        }

    }

    private static void awaitAll(List<Future<Long>> inFlightRequests)
    {
        while (!inFlightRequests.isEmpty())
        {
            poll(inFlightRequests);
        }
    }

    private static void poll(List<Future<Long>> inFlightRequests)
    {
        final Iterator<Future<Long>> iterator = inFlightRequests.iterator();
        while (iterator.hasNext())
        {
            final Future<Long> future = iterator.next();
            if (future.isDone())
            {
                try
                {
                    future.get();
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                catch (ExecutionException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    iterator.remove();
                }
            }
        }
    }

    private static void printProperties(Properties properties)
    {
        System.out.println("Client configuration:");

        final TreeMap<String, String> sortedProperties = new TreeMap<>();

        final Enumeration<?> propertyNames = properties.propertyNames();
        while (propertyNames.hasMoreElements())
        {
            final String key = (String) propertyNames.nextElement();
            final String value = properties.getProperty(key);
            sortedProperties.put(key, value);
        }

        for (Entry<String, String> property : sortedProperties.entrySet())
        {
            System.out.println(String.format("%s: %s", property.getKey(), property.getValue()));
        }

    }

}
