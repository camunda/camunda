package org.camunda.tngp.broker.wf.runtime;

import static org.camunda.tngp.broker.log.LogServiceNames.logServiceName;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.wfDefinitionCacheServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfInstanceIdGeneratorServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfRuntimeWorkflowEventIndexServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfRuntimeContextServiceName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.camunda.tngp.broker.log.LogEntryProcessor;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.Long2LongIndexManagerService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.wf.cfg.WfRuntimeCfg;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
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

    protected final List<LogEntryProcessor<?>> inputLogProcessors = new CopyOnWriteArrayList<>();

    public WfRuntimeManagerService(ConfigurationManager configurationManager)
    {
        super(WfRuntimeContext.class);
        this.configurationManager = configurationManager;
        this.runtimeCfgs = configurationManager.readList("workflow-runtime", WfRuntimeCfg.class);
    }

    @Override
    public void createWorkflowInstanceQueue(WfRuntimeCfg cfg)
    {
        final String wfRuntimeName = cfg.name;
        if (wfRuntimeName == null || wfRuntimeName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime " + wfRuntimeName + ": Configuration property 'name' cannot be null.");
        }

        final int wfRuntimeId = cfg.id;
        if (wfRuntimeId < 0 || wfRuntimeId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start workflow runtime " + wfRuntimeName + ": Invalid value for config property id " + wfRuntimeId + ". Value must be in range [0," + Short.MAX_VALUE + "]");
        }

        final String wfInstancelogName = cfg.logName;
        if (wfInstancelogName == null || wfInstancelogName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime " + wfRuntimeName + ": Mandatory configuration property 'logName' is not set.");
        }

        final String wfRepositoryName = cfg.repositoryName;
        if (wfRepositoryName == null || wfRepositoryName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow runtime " + wfRuntimeName + ": Mandatory configuration property 'repositoryName' is not set.");
        }

        final ServiceName<Log> wfInstanceLogServiceName = logServiceName(wfInstancelogName);
        final ServiceName<IdGenerator> wfInstanceIdGeneratorServiceName = wfInstanceIdGeneratorServiceName(wfRuntimeName);
        final ServiceName<HashIndexManager<Long2LongHashIndex>> workflowEventIndexServiceName = wfRuntimeWorkflowEventIndexServiceName(wfRuntimeName);

        final LogIdGeneratorService wfInstanceIdGeneratorService = new LogIdGeneratorService(new WfInstanceIdReader());
        serviceContext.createService(wfInstanceIdGeneratorServiceName, wfInstanceIdGeneratorService)
            .dependency(wfInstanceLogServiceName, wfInstanceIdGeneratorService.getLogInjector())
            .install();

        final Long2LongIndexManagerService activityInstanceIndexManagerService = new Long2LongIndexManagerService(2048, 64 * 1024);
        serviceContext.createService(workflowEventIndexServiceName, activityInstanceIndexManagerService)
            .dependency(wfInstanceLogServiceName, activityInstanceIndexManagerService.getLogInjector())
            .install();

        final WfRuntimeContextService wfRuntimeContextService = new WfRuntimeContextService(wfRuntimeId, wfRuntimeName);
        serviceContext.createService(wfRuntimeContextServiceName(wfRuntimeName), wfRuntimeContextService)
            .dependency(wfInstanceLogServiceName, wfRuntimeContextService.getLogInjector())
            .dependency(wfInstanceIdGeneratorServiceName, wfRuntimeContextService.getIdGeneratorInjector())
            .dependency(wfDefinitionCacheServiceName(wfRepositoryName), wfRuntimeContextService.getwfDefinitionChacheInjector())
            .dependency(workflowEventIndexServiceName, wfRuntimeContextService.getWorkflowEventIndexInjector())
            .listener(this)
            .install();
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;
        for (WfRuntimeCfg wfRuntimeCfg : runtimeCfgs)
        {
            createWorkflowInstanceQueue(wfRuntimeCfg);
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

    @Override
    public void registerInputLogProcessor(LogEntryProcessor<?> logReadHandler)
    {
        inputLogProcessors.add(logReadHandler);
    }

    @Override
    public List<LogEntryProcessor<?>> getInputLogProcessors()
    {
        return inputLogProcessors;
    }

}
