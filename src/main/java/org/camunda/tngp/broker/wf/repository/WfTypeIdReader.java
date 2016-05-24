package org.camunda.tngp.broker.wf.repository;

import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;

import uk.co.real_logic.agrona.DirectBuffer;

public class WfTypeIdReader implements LogFragmentIdReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final WfTypeDecoder wfTypeDecoder = new WfTypeDecoder();

    @Override
    public int blockLength()
    {
        return headerDecoder.encodedLength() + wfTypeDecoder.sbeBlockLength();
    }

    @Override
    public long getId(DirectBuffer block)
    {
        long id = 0;

        headerDecoder.wrap(block, 0);

        if(headerDecoder.templateId() == wfTypeDecoder.sbeTemplateId())
        {
            wfTypeDecoder.wrap(block, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
            id = wfTypeDecoder.id();
        }

        return id;
    }

}
