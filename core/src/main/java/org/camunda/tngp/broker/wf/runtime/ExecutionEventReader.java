package org.camunda.tngp.broker.wf.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.agrona.io.DirectBufferInputStream;

public class ExecutionEventReader implements LogFragmentHandler
{
    public static final int PAYLOAD_MAX_LENGTH = 256;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final FlowElementExecutionEventDecoder executionEventDecoder = new FlowElementExecutionEventDecoder();

    protected final ByteBuffer blockReadBuffer;
    protected final UnsafeBuffer blockReadBufferView;

    protected final ByteBuffer payloadReadBuffer;
    protected final UnsafeBuffer payloadReadBufferView;

    protected int length;

    protected int payloadLength;
    protected int payloadOffset;

    protected long logPosition;
    protected long offset;
    protected FileChannel fileChannel;

    public ExecutionEventReader()
    {
        int readBufferLength = MessageHeaderDecoder.ENCODED_LENGTH +
                FlowElementExecutionEventDecoder.BLOCK_LENGTH;

        this.blockReadBuffer = ByteBuffer.allocateDirect(readBufferLength);
        this.blockReadBufferView = new UnsafeBuffer(blockReadBuffer);
        this.headerDecoder.wrap(blockReadBufferView, 0);

        this.payloadReadBuffer = ByteBuffer.allocate(PAYLOAD_MAX_LENGTH);
        this.payloadReadBufferView = new UnsafeBuffer(payloadReadBuffer);
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
                executionEventDecoder.wrap(blockReadBufferView, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

//                int limit = executionEventDecoder.limit();
//
//                wfTypeKeyLength = executionEventDecoder.typeKeyLength();
//
//                limit += TaskInstanceDecoder.taskTypeHeaderLength();
//
//                wfTypeKeyOffset = limit;
//
//                limit += wfTypeKeyLength;
//                executionEventDecoder.limit(limit);
//
//                resourceDataLength = executionEventDecoder.resourceLength();
//
//                limit += TaskInstanceDecoder.payloadHeaderLength();
//
//                resourceOffset = limit;
            }
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

//    public boolean readPayload()
//    {
//        boolean wasRead = false;
//
//        payloadReadBuffer.position(0);
//        payloadReadBuffer.limit(resourceDataLength);
//
//        try
//        {
//            fileChannel.read(payloadReadBuffer, offset + resourceOffset);
//            wasRead = true;
//        }
//        catch (IOException e)
//        {
//            LangUtil.rethrowUnchecked(e);
//        }
//
//        return wasRead;
//    }

    public FlowElementExecutionEventDecoder getDecoder()
    {
        return executionEventDecoder;
    }

    public DirectBuffer getBlockBuffer()
    {
        return blockReadBufferView;
    }

    public DirectBuffer getPayloadBuffer()
    {
        return payloadReadBufferView;
    }

    public static int getPayloadMaxLength()
    {
        return PAYLOAD_MAX_LENGTH;
    }

    public MessageHeaderDecoder getHeaderDecoder()
    {
        return headerDecoder;
    }

    public int getLength()
    {
        return length;
    }

    public long getLogPosition()
    {
        return logPosition;
    }

}
