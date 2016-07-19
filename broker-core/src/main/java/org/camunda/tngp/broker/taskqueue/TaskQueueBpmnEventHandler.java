package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnActivityEventReader;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.graph.bpmn.ExecutionEventType;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.data.BpmnActivityEventDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

import uk.co.real_logic.agrona.DirectBuffer;

public class TaskQueueBpmnEventHandler implements LogEntryHandler<BpmnEventReader>
{
    protected ResourceContextProvider<TaskQueueContext> taskQueueContextProvider;
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    public TaskQueueBpmnEventHandler(ResourceContextProvider<TaskQueueContext> taskQueueContextProvider)
    {
        this.taskQueueContextProvider = taskQueueContextProvider;
    }

    @Override
    public void handle(long position, BpmnEventReader reader)
    {
        if (reader.templateId() == BpmnActivityEventDecoder.TEMPLATE_ID
                && reader.activityEvent().event() == ExecutionEventType.ACT_INST_CREATED)
        {
            final BpmnActivityEventReader activityEventReader = reader.activityEvent();

            final TaskQueueContext taskQueueContext = taskQueueContextProvider.getContextForResource(activityEventReader.taskQueueId());
            final IdGenerator taskQueueIdGenerator = taskQueueContext.getTaskInstanceIdGenerator();
            final LogWriter logWriter = taskQueueContext.getLogWriter();

            final DirectBuffer taskType = activityEventReader.getTaskType();
            taskInstanceWriter
                .id(taskQueueIdGenerator.nextId())
                .taskType(taskType, 0, taskType.capacity())
                .state(TaskInstanceState.NEW)
                .wfRuntimeResourceId(activityEventReader.resourceId())
                .wfActivityInstanceEventKey(activityEventReader.key());

            logWriter.write(taskInstanceWriter);
        }
    }

    public void setTaskInstanceWriter(TaskInstanceWriter taskInstanceWriter)
    {
        this.taskInstanceWriter = taskInstanceWriter;
    }
}
