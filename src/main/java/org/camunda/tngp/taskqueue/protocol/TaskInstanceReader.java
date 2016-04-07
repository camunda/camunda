package org.camunda.tngp.taskqueue.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceReader
{
    public static final int TASK_TYPE_MAXLENGTH = 64;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskInstanceDecoder taskInstanceDecoder = new TaskInstanceDecoder();

    protected final ByteBuffer readBuffer;
    protected final UnsafeBuffer readBufferView;

    protected int length;

    protected int taskTypeLength;
    protected int taskTypeOffset;
    protected int payloadLength;
    protected int payloadOffset;

    public TaskInstanceReader()
    {
        int readBufferLength = MessageHeaderDecoder.ENCODED_LENGTH +
                TaskInstanceDecoder.BLOCK_LENGTH +
                TASK_TYPE_MAXLENGTH +
                TaskInstanceDecoder.taskTypeHeaderLength();

        this.readBuffer = ByteBuffer.allocateDirect(readBufferLength);
        this.readBufferView = new UnsafeBuffer(readBuffer);
        this.headerDecoder.wrap(readBufferView, 0);
    }

    public void reset()
    {
        this.length = 0;
    }

    public boolean read(FileChannel fileChannel, long offset, int length)
    {
        this.length = length;
        readBuffer.clear();
        readBuffer.position(0);
        readBuffer.limit(length);

        boolean wasRead = false;

        try
        {
            if(fileChannel.read(readBuffer, offset) == length)
            {
                if(headerDecoder.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
                {
                    taskInstanceDecoder.wrap(readBufferView, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

                    int limit = taskInstanceDecoder.limit();

                    taskTypeLength = taskInstanceDecoder.taskTypeLength();

                    limit += TaskInstanceDecoder.taskTypeHeaderLength();

                    taskTypeOffset = limit;

                    limit += taskTypeLength;
                    taskInstanceDecoder.limit(limit);

                    payloadLength = taskInstanceDecoder.payloadLength();

                    limit += TaskInstanceDecoder.payloadHeaderLength();

                    payloadOffset = limit;

                    wasRead = true;
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return wasRead;
    }

    public TaskInstanceDecoder getDecoder()
    {
        return taskInstanceDecoder;
    }

    public DirectBuffer getReadBuffer()
    {
        return readBufferView;
    }

    public static int getTaskTypeMaxlength()
    {
        return TASK_TYPE_MAXLENGTH;
    }

    public MessageHeaderDecoder getHeaderDecoder()
    {
        return headerDecoder;
    }

    public TaskInstanceDecoder getTaskInstanceDecoder()
    {
        return taskInstanceDecoder;
    }

    public UnsafeBuffer getReadBufferView()
    {
        return readBufferView;
    }

    public int getLength()
    {
        return length;
    }

    public int getTaskTypeLength()
    {
        return taskTypeLength;
    }

    public int getTaskTypeOffset()
    {
        return taskTypeOffset;
    }

    public int getPayloadLength()
    {
        return payloadLength;
    }

    public int getPayloadOffset()
    {
        return payloadOffset;
    }

}
