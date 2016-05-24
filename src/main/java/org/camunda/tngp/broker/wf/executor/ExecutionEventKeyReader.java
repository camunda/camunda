package org.camunda.tngp.broker.wf.executor;

import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class ExecutionEventKeyReader implements LogFragmentIdReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final FlowElementExecutionEventDecoder decoder = new FlowElementExecutionEventDecoder();

    @Override
    public int blockLength()
    {
        return headerDecoder.encodedLength() + decoder.sbeBlockLength();
    }

    @Override
    public long getId(DirectBuffer block)
    {
        long id = 0;

        headerDecoder.wrap(block, 0);

        if(headerDecoder.templateId() == decoder.sbeTemplateId())
        {
            decoder.wrap(block, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
            id = decoder.key();
        }

        return id;
    }

}
