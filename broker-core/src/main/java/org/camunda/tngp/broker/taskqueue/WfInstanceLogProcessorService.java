package org.camunda.tngp.broker.taskqueue;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.services.LogEntryProcessorService;
import org.camunda.tngp.broker.wf.runtime.bpmn.event.BpmnEventReader;
import org.camunda.tngp.log.fs.LogSegmentDescriptor;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class WfInstanceLogProcessorService extends LogEntryProcessorService<BpmnEventReader>
{
    protected final Injector<TaskQueueManager> taskQueueManager = new Injector<>();

    public WfInstanceLogProcessorService()
    {
        super(new BpmnEventReader());
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        super.start(serviceContext);

        taskQueueManager.getValue().registerInputLogProcessor(logEntryProcessor);
    }

    @Override
    protected LogEntryHandler<BpmnEventReader> createEntryHandler()
    {
        return new TaskQueueBpmnEventHandler(taskQueueManager.getValue());
    }

    @Override
    protected int recoverLastReadPosition()
    {
        // TODO: recover actual position that was last read
        // TODO: also, it is not so nice that we have to worry about
        //   log metadata layout on this level of abstraction
        return LogSegmentDescriptor.METADATA_LENGTH;
    }

    public Injector<TaskQueueManager> getTaskQueueManager()
    {
        return taskQueueManager;
    }
}
