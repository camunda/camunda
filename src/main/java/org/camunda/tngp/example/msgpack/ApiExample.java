package org.camunda.tngp.example.msgpack;

import java.io.IOException;
import java.util.Arrays;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

public class ApiExample
{
    public static void main(String[] args) throws IOException
    {
        // Serialize with MessagePacker.
        // MessageBufferPacker is an optimized version of MessagePacker for packing data into a byte array
        MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packer
                .packInt(1);
        packer.close(); // Never forget to close (or flush) the buffer

        byte[] bytes = packer.toByteArray();
        System.out.println(Arrays.toString(bytes));
    }


}
