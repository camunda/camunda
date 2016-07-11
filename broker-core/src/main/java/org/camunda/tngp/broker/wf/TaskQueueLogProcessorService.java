package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.services.LogEntryProcessorService;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeManager;
import org.camunda.tngp.log.fs.LogSegmentDescriptor;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class TaskQueueLogProcessorService extends LogEntryProcessorService<TaskInstanceReader>
{

    protected Injector<WfRuntimeManager> wfRuntimeManager = new Injector<>();

    public TaskQueueLogProcessorService()
    {
        super(new TaskInstanceReader());
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        super.start(serviceContext);

        wfRuntimeManager.getValue().registerInputLogProcessor(logEntryProcessor);
    }

    @Override
    protected int recoverLastReadPosition()
    {
        // TODO: recover actual position that was last read
        // TODO: also, it is not so nice that we have to worry about
        //   log metadata layout on this level of abstraction
        return LogSegmentDescriptor.METADATA_LENGTH;
    }

    @Override
    protected LogEntryHandler<TaskInstanceReader> createEntryHandler()
    {
        return new WorkflowTaskEventHandler(wfRuntimeManager.getValue());
    }

    public Injector<WfRuntimeManager> getWfRuntimeManager()
    {
        return wfRuntimeManager;
    }

}
