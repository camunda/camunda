package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogEntryProcessorService;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.ServiceName;

public class WfRuntimeServiceNames
{
    public static final ServiceName<WfRuntimeManager> WF_RUNTIME_MANAGER_NAME = ServiceName.newServiceName("wf.runtime.manager", WfRuntimeManager.class);

    public static ServiceName<IdGenerator> wfInstanceIdGeneratorServiceName(String runtimeName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.%s.instance.id-generator", runtimeName), IdGenerator.class);
    }

    public static ServiceName<WfRuntimeContext> wfRuntimeContextServiceName(String runtimeName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.%s.context", runtimeName), WfRuntimeContext.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName<LogEntryProcessor<TaskInstanceReader>> taskEventHandlerService(String logName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.runtime.reader.%s", logName), LogEntryProcessorService.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName<HashIndexManager<Long2LongHashIndex>> wfRuntimeActivityInstanceEventIndexServiceName(String runtimeName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.runtime.%s.index.activityInstances", runtimeName), HashIndexManager.class);
    }


}
