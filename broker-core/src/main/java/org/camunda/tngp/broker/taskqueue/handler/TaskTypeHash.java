package org.camunda.tngp.broker.taskqueue.handler;

public class TaskTypeHash
{

    public static int hashCode(byte[] array, int length)
    {
        int result = 1;

        for (int i = 0; i < length; i++)
        {
            result = 31 * result + array[i];
        }

        return result;
    }
}
