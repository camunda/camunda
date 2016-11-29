package org.camunda.tngp.broker.taskqueue.processor;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.logstreams.requests.LogStreamRequestQueue;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.logstreams.LoggedEvent;
import org.camunda.tngp.logstreams.processor.EventProcessingActions;
import org.camunda.tngp.logstreams.processor.StreamProcessor;
import org.camunda.tngp.logstreams.processor.StreamProcessorAction;
import org.camunda.tngp.logstreams.processor.StreamProcessorContext;
import org.camunda.tngp.protocol.clientapi.MessageHeaderEncoder;
import org.camunda.tngp.protocol.clientapi.TaskCmdDecoder;
import org.camunda.tngp.protocol.clientapi.TaskCmdEncoder;
import org.camunda.tngp.protocol.clientapi.TaskCommandType;
import org.camunda.tngp.protocol.clientapi.TaskDataDecoder;
import org.camunda.tngp.protocol.clientapi.TaskEventDecoder;
import org.camunda.tngp.protocol.clientapi.TaskEventEncoder;

import uk.co.real_logic.sbe.ir.generated.MessageHeaderDecoder;

public class TaskInstanceStreamProcessor implements StreamProcessor<TaskInstanceEventContext>
{
    protected LogStreamRequestQueue requestqueue;
    protected Dispatcher responseSendBuffer;

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    protected final TaskCmdDecoder taskCmdDecoder = new TaskCmdDecoder();
    protected final TaskCmdEncoder taskCmdEncoder = new TaskCmdEncoder();

    protected final TaskEventDecoder taskEventDecoder = new TaskEventDecoder();
    protected final TaskEventEncoder taskEventEncoder = new TaskEventEncoder();

    protected Long2LongHashIndex lastPositionIndex;

    protected final ProcessCreateTaskCmd processCreateTaskCmd = new ProcessCreateTaskCmd();


    @Override
    public void open(StreamProcessorContext streamProcessorContext)
    {

    }

    @Override
    public TaskInstanceEventContext createContext()
    {
        return new TaskInstanceEventContext();
    }

    @Override
    public void handleEvent(LoggedEvent event, TaskInstanceEventContext context, EventProcessingActions<TaskInstanceEventContext> eventProcessingActions)
    {
        final DirectBuffer buffer = event.getValueBuffer();
        int offset = event.getValueOffset();

        headerDecoder.wrap(buffer, offset);
        offset += headerDecoder.encodedLength();

        final int templateId = headerDecoder.templateId();

        if (taskCmdDecoder.sbeTemplateId() == templateId)
        {
            taskCmdDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

            readTaskData(taskCmdDecoder.taskData(), context);

            final TaskCommandType cmdType = taskCmdDecoder.cmdType();
            switch (cmdType)
            {
                case CREATE:

                    eventProcessingActions
                        .processEvent(processCreateTaskCmd)
                        .executeSideEffects(null)
                        .writeEvents(null)
                        .updateState(null);

                    break;

                default:
                    break;
            }
        }
        else
        {
            taskEventDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        }
    }

    private void readTaskData(TaskDataDecoder taskDataDecoder, TaskInstanceEventContext context)
    {
        taskDataDecoder.
    }

    class ProcessCreateTaskCmd implements StreamProcessorAction<TaskInstanceEventContext>
    {

        @Override
        public boolean execute(TaskInstanceEventContext ctx)
        {

            return true;
        }

    }

}
