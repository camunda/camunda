package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;

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

    public static int hashCode(DirectBuffer buffer, int offset, int length)
    {
        int result = 1;

        for (int i = offset; i < offset + length; i++)
        {
            result = 31 * result + buffer.getByte(i);
        }

        return result;
    }
}
