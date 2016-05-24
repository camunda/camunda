package org.camunda.tngp.broker.wf.repository;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.io.DirectBufferInputStream;

public class WfTypeReader implements LogFragmentHandler
{
    public static final int WF_KEY_MAXLENGTH = 256;
    public static final int DATA_MAXLENGTH = 1024 * 1014;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final WfTypeDecoder wfTypeDecoder = new WfTypeDecoder();

    protected final ByteBuffer blockReadBuffer;
    protected final UnsafeBuffer blockReadBufferView;

    protected final ByteBuffer resourceReadBuffer;
    protected final UnsafeBuffer resourceReadBufferView;

    protected int length;

    protected int wfTypeKeyLength;
    protected int wfTypeKeyOffset;
    protected int resourceDataLength;
    protected int resourceOffset;

    protected long logPosition;
    protected long offset;
    protected FileChannel fileChannel;

    public WfTypeReader()
    {
        int readBufferLength = MessageHeaderDecoder.ENCODED_LENGTH +
                WfTypeDecoder.BLOCK_LENGTH +
                WF_KEY_MAXLENGTH +
                TaskInstanceDecoder.taskTypeHeaderLength();

        this.blockReadBuffer = ByteBuffer.allocateDirect(readBufferLength);
        this.blockReadBufferView = new UnsafeBuffer(blockReadBuffer);
        this.headerDecoder.wrap(blockReadBufferView, 0);

        this.resourceReadBuffer = ByteBuffer.allocate(DATA_MAXLENGTH);
        this.resourceReadBufferView = new UnsafeBuffer(resourceReadBuffer);
    }

    public void reset()
    {
        this.length = 0;
        this.fileChannel = null;
        this.offset = 0;
        this.logPosition = 0;
    }

    @Override
    public void onFragment(long position, FileChannel fileChannel, int offset, int length)
    {
        this.logPosition = position;
        this.fileChannel = fileChannel;
        this.offset = offset;
        this.length = length;
        blockReadBuffer.position(0);
        blockReadBuffer.limit(Math.min(length, blockReadBuffer.capacity()));

        try
        {
            fileChannel.read(blockReadBuffer, offset);

            if(headerDecoder.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
            {
                wfTypeDecoder.wrap(blockReadBufferView, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

                int limit = wfTypeDecoder.limit();

                wfTypeKeyLength = wfTypeDecoder.typeKeyLength();

                limit += TaskInstanceDecoder.taskTypeHeaderLength();

                wfTypeKeyOffset = limit;

                limit += wfTypeKeyLength;
                wfTypeDecoder.limit(limit);

                resourceDataLength = wfTypeDecoder.resourceLength();

                limit += TaskInstanceDecoder.payloadHeaderLength();

                resourceOffset = limit;
            }
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public boolean readResourceData()
    {
        boolean wasRead = false;

        resourceReadBuffer.position(0);
        resourceReadBuffer.limit(resourceDataLength);

        try
        {
            fileChannel.read(resourceReadBuffer, offset + resourceOffset);
            wasRead = true;
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

        return wasRead;
    }

    public WfTypeDecoder getDecoder()
    {
        return wfTypeDecoder;
    }

    public DirectBuffer getBlockBuffer()
    {
        return blockReadBufferView;
    }

    public DirectBuffer getResourceBuffer()
    {
        return resourceReadBufferView;
    }

    public static int getWfTypeMaxlength()
    {
        return WF_KEY_MAXLENGTH;
    }

    public MessageHeaderDecoder getHeaderDecoder()
    {
        return headerDecoder;
    }

    public int getLength()
    {
        return length;
    }

    public int getWfTypeKeyLength()
    {
        return wfTypeKeyLength;
    }

    public int getWfTypeKeyOffset()
    {
        return wfTypeKeyOffset;
    }

    public int getWfResourceLength()
    {
        return resourceDataLength;
    }

    public int getWfResourceOffset()
    {
        return resourceOffset;
    }

    public long getLogPosition()
    {
        return logPosition;
    }

    public BpmnModelInstance asModelInstance()
    {
        readResourceData();
        return Bpmn.readModelFromStream(new DirectBufferInputStream(resourceReadBufferView, 0, resourceDataLength));
    }
}
