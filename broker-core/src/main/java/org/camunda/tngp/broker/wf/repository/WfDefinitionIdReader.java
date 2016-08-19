package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionDecoder;

import org.agrona.DirectBuffer;

public class WfDefinitionIdReader implements LogFragmentIdReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final WfDefinitionDecoder wfDefinitionDecoder = new WfDefinitionDecoder();

    @Override
    public long getId(DirectBuffer block)
    {
        long id = 0;

        headerDecoder.wrap(block, 0);

        if (headerDecoder.templateId() == wfDefinitionDecoder.sbeTemplateId())
        {
            wfDefinitionDecoder.wrap(block, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
            id = wfDefinitionDecoder.id();
        }

        return id;
    }

}
