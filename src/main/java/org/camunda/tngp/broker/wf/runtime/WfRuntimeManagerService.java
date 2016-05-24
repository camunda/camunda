package org.camunda.tngp.broker.wf.runtime;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.*;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.*;

import java.util.List;

import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.wf.cfg.WfRuntimeCfg;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.servicecontainer.ServiceName;

public class WfRuntimeManagerService
    extends
        AbstractResourceContextProvider<WfRuntimeContext>
    implements
        Service<WfRuntimeManager>,
        WfRuntimeManager
{

    protected final ConfigurationManager configurationManager;
    protected final List<WfRuntimeCfg> runtimeCfgs;

    protected ServiceContext serviceContext;

    public WfRuntimeManagerService(ConfigurationManager configurationManager)
    {
        super(WfRuntimeContext.class);
        this.configurationManager = configurationManager;
        this.runtimeCfgs = configurationManager.readList("workflow-runtime", WfRuntimeCfg.class);
    }

    @Override
    public void createRepository(WfRuntimeCfg cfg)
    {
        final String wfRuntimeName = cfg.name;
        if(wfRuntimeName == null || wfRuntimeName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime "+wfRuntimeName+": Configuration property 'name' cannot be null.");
        }

        final int wfRuntimeId = cfg.id;
        if(wfRuntimeId < 0 || wfRuntimeId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start workflow runtime " + wfRuntimeName + ": Invalid value for config property id "+ wfRuntimeId+ ". Value must be in range [0,"+Short.MAX_VALUE+"]");
        }

        final String wfInstancelogName = cfg.logName;
        if(wfInstancelogName == null || wfInstancelogName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime "+wfRuntimeName+": Mandatory configuration property 'logName' is not set.");
        }

        final String wfRepositoryName = cfg.repositoryName;
        if(wfRepositoryName == null || wfRepositoryName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime "+wfRuntimeName+": Mandatory configuration property 'repositoryName' is not set.");
        }

        final ServiceName<Log> wfInstanceLogServiceName = logServiceName(wfInstancelogName);
        final ServiceName<IdGenerator> wfInstanceIdGeneratorServiceName = wfInstanceIdGeneratorServiceName(wfRuntimeName);

        final LogIdGeneratorService wfInstanceIdGeneratorService = new LogIdGeneratorService(new WfInstanceIdReader());
        serviceContext.createService(wfInstanceIdGeneratorServiceName, wfInstanceIdGeneratorService)
            .dependency(wfInstanceLogServiceName, wfInstanceIdGeneratorService.getLogInjector())
            .install();

        final WfRuntimeContextService wfRuntimeContextService = new WfRuntimeContextService(wfRuntimeId, wfRuntimeName);
        serviceContext.createService(wfRuntimeContextServiceName(wfRuntimeName), wfRuntimeContextService)
            .dependency(wfInstanceLogServiceName, wfRuntimeContextService.getLogInjector())
            .dependency(wfInstanceIdGeneratorServiceName, wfRuntimeContextService.getIdGeneratorInjector())
            .dependency(wfTypeCacheServiceName(wfRepositoryName), wfRuntimeContextService.getWfTypeChacheInjector())
            .listener(this)
            .install();
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;
        for (WfRuntimeCfg wfRuntimeCfg : runtimeCfgs)
        {
            createRepository(wfRuntimeCfg);
        }
    }

    @Override
    public void stop()
    {

    }

    @Override
    public WfRuntimeManager get()
    {
        return this;
    }

}
