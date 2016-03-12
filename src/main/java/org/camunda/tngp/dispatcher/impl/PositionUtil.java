package org.camunda.tngp.dispatcher.impl;

/**
 * Utility for composing the position
 *
 */
public class PositionUtil
{

    public static long position(int partitionId, int partitionOffset)
    {
        return ((long) partitionId) << 32 | partitionOffset & 0xFFFFFFFFL;
    }

    public static int partitionId(long position)
    {
        return (int) (position >> 32);
    }

    public static int partitionOffset(long position)
    {
        return (int) (position & 0xFFFFFFFFL);
    }

}
