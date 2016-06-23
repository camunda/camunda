package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.graph.bpmn.FlowElementType;
import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventDecoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfInstanceIdReader implements LogFragmentIdReader
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

        if (headerDecoder.templateId() == decoder.sbeTemplateId())
        {
            decoder.wrap(block, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

            final long key = decoder.key();
            final int flowElementType = decoder.flowElementType();
            final int event = decoder.event();

            if (isProcessInstanceStart(flowElementType, event))
            {
                id = key;
            }

        }

        return id;
    }

    private static boolean isProcessInstanceStart(final int flowElementType, final int event)
    {
        return FlowElementType.PROCESS.value() == flowElementType && ExecutionEventType.PROC_INST_CREATED.value() == event;
    }

}
