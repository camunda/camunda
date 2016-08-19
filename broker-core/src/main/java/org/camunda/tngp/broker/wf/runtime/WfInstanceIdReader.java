package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnProcessEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;
import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;

import org.agrona.DirectBuffer;

public class WfInstanceIdReader implements LogFragmentIdReader
{

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final BpmnProcessEventReader eventReader = new BpmnProcessEventReader();

    @Override
    public long getId(DirectBuffer block)
    {
        final long id = 0;

        headerDecoder.wrap(block, 0);

        // TODO: which event is the right one to reconstruct the last process instance id
        //  => the first event that uses the process instance id
        //  => should we ensure that this is always the same event to simplify the logic?
//        if(headerDecoder.templateId() == BpmnProcessEventDecoder.TEMPLATE_ID)
//        {
//            // TODO: read event and id here from eventReader
//
//            if(isCreateEvent(event))
//            {
//                id = ..;
//            }
//
//        }

        return id;
    }

    private static boolean isCreateEvent(final int event)
    {
        return  ExecutionEventType.PROC_INST_CREATED.value() == event;
    }

}
