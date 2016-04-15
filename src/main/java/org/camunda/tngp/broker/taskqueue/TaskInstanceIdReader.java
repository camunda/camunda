package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.sbe.ir.generated.MessageHeaderDecoder;

public class TaskInstanceIdReader implements LogFragmentIdReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final TaskInstanceDecoder taskInstanceDecoder = new TaskInstanceDecoder();

    @Override
    public int blockLength()
    {
        return headerDecoder.encodedLength() + taskInstanceDecoder.sbeBlockLength();
    }

    @Override
    public long getId(DirectBuffer block)
    {
        long id = 0;

        headerDecoder.wrap(block, 0);

        if(headerDecoder.templateId() == taskInstanceDecoder.sbeTemplateId())
        {
            taskInstanceDecoder.wrap(block, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

            if(taskInstanceDecoder.state() == TaskInstanceState.NEW)
            {
                id = taskInstanceDecoder.id();
            }
        }

        return id;
    }

}
