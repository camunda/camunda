package org.camunda.tngp.broker.taskqueue.log.handler;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.log.bpmn.BpmnActivityEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import uk.co.real_logic.agrona.DirectBuffer;

public class InputActivityInstanceHandler implements LogEntryTypeHandler<BpmnActivityEventReader>
{
    protected ResourceContextProvider<TaskQueueContext> taskQueueContextProvider;
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    public InputActivityInstanceHandler(ResourceContextProvider<TaskQueueContext> taskQueueContextProvider)
    {
        this.taskQueueContextProvider = taskQueueContextProvider;
    }

    @Override
    public void handle(BpmnActivityEventReader reader, ResponseControl responseControl)
    {
        if (reader.event() == ExecutionEventType.ACT_INST_CREATED)
        {
            final TaskQueueContext taskQueueContext = taskQueueContextProvider.getContextForResource(reader.taskQueueId());
            final IdGenerator taskQueueIdGenerator = taskQueueContext.getTaskInstanceIdGenerator();
            final LogWriter logWriter = taskQueueContext.getLogWriter();

            final DirectBuffer taskType = reader.getTaskType();
            taskInstanceWriter
                .source(EventSource.EXTERNAL_LOG)
                .id(taskQueueIdGenerator.nextId())
                .taskType(taskType, 0, taskType.capacity())
                .state(TaskInstanceState.NEW)
                .wfRuntimeResourceId(reader.resourceId())
                .wfActivityInstanceEventKey(reader.key());

            logWriter.write(taskInstanceWriter);
        }
    }
}
