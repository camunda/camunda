package org.camunda.tngp.broker.taskqueue.log.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.log.TaskInstanceState;

public class InputActivityInstanceHandler implements LogEntryTypeHandler<BpmnActivityEventReader>
{
    protected ResourceContextProvider<TaskQueueContext> taskQueueContextProvider;
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    public InputActivityInstanceHandler(ResourceContextProvider<TaskQueueContext> taskQueueContextProvider)
    {
        this.taskQueueContextProvider = taskQueueContextProvider;
    }

    @Override
    public void handle(BpmnActivityEventReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        if (reader.event() == ExecutionEventType.ACT_INST_CREATED)
        {
            // TODO: this is not so great yet: we have the logWriters abstraction but still need the resource context provider
            //   here to access the id generator
            final TaskQueueContext taskQueueContext = taskQueueContextProvider.getContextForResource(reader.taskQueueId());
            if (taskQueueContext != null)
            {
                final IdGenerator taskQueueIdGenerator = taskQueueContext.getTaskInstanceIdGenerator();

                final DirectBuffer taskType = reader.getTaskType();
                final DirectBuffer payload = reader.getPayload();
                taskInstanceWriter
                    .id(taskQueueIdGenerator.nextId())
                    .taskType(taskType, 0, taskType.capacity())
                    .payload(payload, 0, payload.capacity())
                    .state(TaskInstanceState.NEW)
                    .wfRuntimeResourceId(reader.resourceId())
                    .wfActivityInstanceEventKey(reader.key())
                    .wfInstanceId(reader.wfInstanceId());

                logWriters.writeToLog(reader.taskQueueId(), taskInstanceWriter);
            }
        }
    }
}
