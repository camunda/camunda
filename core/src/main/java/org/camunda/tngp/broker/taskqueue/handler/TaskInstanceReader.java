package org.camunda.tngp.broker.taskqueue.handler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TaskInstanceReader
{
    public static final int TASK_TYPE_MAXLENGTH = 256;
    public static final int PAYLOAD_MAXLENGTH = 1024 * 16;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskInstanceDecoder taskInstanceDecoder = new TaskInstanceDecoder();

    protected final ByteBuffer blockReadBuffer;
    protected final UnsafeBuffer blockReadBufferView;


    protected final ByteBuffer payloadReadBuffer;
    protected final UnsafeBuffer payloadReadBufferView;

    protected int length;

    protected int taskTypeLength;
    protected int taskTypeOffset;
    protected int payloadLength;
    protected int payloadOffset;

    protected long logPosition;
    protected long offset;
    protected FileChannel fileChannel;

    public TaskInstanceReader()
    {
        int readBufferLength = MessageHeaderDecoder.ENCODED_LENGTH +
                TaskInstanceDecoder.BLOCK_LENGTH +
                TASK_TYPE_MAXLENGTH +
                TaskInstanceDecoder.taskTypeHeaderLength();

        this.blockReadBuffer = ByteBuffer.allocateDirect(readBufferLength);
        this.blockReadBufferView = new UnsafeBuffer(blockReadBuffer);
        this.headerDecoder.wrap(blockReadBufferView, 0);

        this.payloadReadBuffer = ByteBuffer.allocateDirect(PAYLOAD_MAXLENGTH);
        this.payloadReadBufferView = new UnsafeBuffer(payloadReadBuffer);
    }

    public void reset()
    {
        this.length = 0;
        this.fileChannel = null;
        this.offset = 0;
        this.logPosition = 0;
    }

    public boolean readBlock(long position, FileChannel fileChannel, long offset, int length)
    {
        this.logPosition = position;
        this.fileChannel = fileChannel;
        this.offset = offset;
        this.length = length;
        blockReadBuffer.position(0);
        blockReadBuffer.limit(Math.min(length, blockReadBuffer.capacity()));

        boolean wasRead = false;

        try
        {
            fileChannel.read(blockReadBuffer, offset);

            if(headerDecoder.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
            {
                taskInstanceDecoder.wrap(blockReadBufferView, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

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
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return wasRead;
    }

    public boolean readPayload()
    {
        boolean wasRead = false;

        payloadReadBuffer.position(0);
        payloadReadBuffer.limit(payloadLength);

        try
        {
            fileChannel.read(payloadReadBuffer, offset + payloadOffset);
            wasRead = true;
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

    public DirectBuffer getBlockBuffer()
    {
        return blockReadBufferView;
    }

    public DirectBuffer getPayloadReadBuffer()
    {
        return payloadReadBufferView;
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

    public long getLogPosition()
    {
        return logPosition;
    }



}
