package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.ServiceName;

public class WfRuntimeServiceNames
{

    public static ServiceName<WfRuntimeManager> WF_RUNTIME_MANAGER_NAME = ServiceName.newServiceName("wf.runtime.manager", WfRuntimeManager.class);

    public final static ServiceName<IdGenerator> wfInstanceIdGeneratorServiceName(String runtimeName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.%s.instance.id-generator", runtimeName), IdGenerator.class);
    }

    public final static ServiceName<WfRuntimeContext> wfRuntimeContextServiceName(String runtimeName)
    {
        return ServiceName.newServiceName(String.format("wf.runtime.%s.context", runtimeName), WfRuntimeContext.class);
    }

}
