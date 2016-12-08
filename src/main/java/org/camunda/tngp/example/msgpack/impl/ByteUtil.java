package org.camunda.tngp.example.msgpack.impl;

import org.agrona.DirectBuffer;

public class ByteUtil
{

    public static boolean equal(byte[] arr1, DirectBuffer buf2, int buf2Offset, int buf2Length)
    {
        if (arr1.length != buf2Length)
        {
            return false;
        }
        else
        {
            boolean equal = true;
            for (int i = 0; i < arr1.length && equal; i++)
            {
                equal = arr1[i] == buf2.getByte(i);
            }
            return equal;
        }

    }

    public static String bytesToBinary(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++)
        {
            String binaryString = Integer.toBinaryString(Byte.toUnsignedInt(bytes[i]));
            int missingLeadingZeroes = 8 - binaryString.length();
            for (int j = 0; j < missingLeadingZeroes; j++)
            {
                sb.append("0");
            }
            sb.append(binaryString);
            sb.append(", ");
        }
        return sb.toString();
    }
}
