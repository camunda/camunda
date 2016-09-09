package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
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

    public static ServiceName<LogConsumer> taskEventHandlerService(String logName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.reader.%s", logName), LogConsumer.class);
    }

    public static ServiceName<LogConsumer> wfDefinitionEventHandlerService(String logName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.reader.%s", logName), LogConsumer.class);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName<HashIndexManager<Long2LongHashIndex>> wfRuntimeWorkflowEventIndexServiceName(String runtimeName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.runtime.%s.index.workflowEvents", runtimeName), HashIndexManager.class);
    }

    public static ServiceName<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexServiceName(String contextName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.repository.%s.definition.id-index", contextName), HashIndexManager.class);
    }

    public static ServiceName<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexServiceName(String contextName)
    {
        return (ServiceName) ServiceName.newServiceName(String.format("wf.repository.%s.definition.key-index", contextName), HashIndexManager.class);
    }

    public static ServiceName<WfDefinitionCache> wfDefinitionCacheServiceName(String contextName)
    {
        return ServiceName.newServiceName(String.format("wf.repository.%s.definition.cache", contextName), WfDefinitionCache.class);
    }


}
