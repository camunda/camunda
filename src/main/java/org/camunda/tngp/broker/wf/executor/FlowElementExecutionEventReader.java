package org.camunda.tngp.broker.wf.executor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.graph.bpmn.FlowElementDescriptorEncoder;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;

import uk.co.real_logic.agrona.LangUtil;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class FlowElementExecutionEventReader implements LogFragmentHandler
{
    protected final static int ENCODED_ENTRY_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH + FlowElementDescriptorEncoder.BLOCK_LENGTH;

    protected final ByteBuffer entryReadBuffer = ByteBuffer.allocate(ENCODED_ENTRY_LENGTH);
    protected final UnsafeBuffer entryReadBufferView = new UnsafeBuffer(entryReadBuffer);

    protected final FlowElementExecutionEventDecoder decoder = new FlowElementExecutionEventDecoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

    protected boolean wasRead = false;

    public FlowElementExecutionEventReader()
    {
        headerDecoder.wrap(entryReadBufferView, 0);
    }

    @Override
    public void onFragment(long position, FileChannel fileChannel, int offset, int length)
    {
        wasRead = false;
        entryReadBuffer.clear();

        try
        {
            wasRead = fileChannel.read(entryReadBuffer, position) == ENCODED_ENTRY_LENGTH;

            if(wasRead)
            {
                decoder.wrap(entryReadBufferView, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
            }
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }

    }

    public boolean wasRead() {
        return wasRead;
    }

    public FlowElementExecutionEventDecoder decoder()
    {
        return decoder;
    }

}
