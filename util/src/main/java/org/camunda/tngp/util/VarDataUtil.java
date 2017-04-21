package org.camunda.tngp.util;

public final class VarDataUtil
{

    public static byte[] readBytes(final VarDataReader reader, final VarDataLengthProvider lengthProvider)
    {
        return readBytes(reader, lengthProvider.length());
    }

    public static byte[] readBytes(final VarDataReader reader, final int length)
    {
        return readBytes(reader, 0, length);
    }

    public static byte[] readBytes(final VarDataReader reader, final int offset, final int length)
    {
        final byte[] buffer = new byte[length];
        reader.decode(buffer, offset, length);
        return buffer;
    }

    @FunctionalInterface
    public interface VarDataLengthProvider
    {
        int length();
    }

    @FunctionalInterface
    public interface VarDataReader
    {
        int decode(byte[] buffer, int offset, int length);
    }
}
