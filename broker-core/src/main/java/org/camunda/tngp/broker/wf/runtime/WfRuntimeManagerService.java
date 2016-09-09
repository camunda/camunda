package org.camunda.tngp.broker.wf.runtime;

import static org.camunda.tngp.broker.log.LogServiceNames.logServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerResponsePoolServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfDefinitionCacheServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfDefinitionIdIndexServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfDefinitionKeyIndexServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfInstanceIdGeneratorServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfRuntimeContextServiceName;
import static org.camunda.tngp.broker.wf.runtime.WfRuntimeServiceNames.wfRuntimeWorkflowEventIndexServiceName;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.camunda.tngp.broker.log.LogConsumer;
import org.camunda.tngp.broker.services.Bytes2LongIndexManagerService;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.Long2LongIndexManagerService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.wf.WfComponent;
import org.camunda.tngp.broker.wf.cfg.WfRuntimeCfg;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
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

    protected final List<LogConsumer> inputLogConsumers = new CopyOnWriteArrayList<>();

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
        final ServiceName<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexServiceName = wfDefinitionIdIndexServiceName(wfRuntimeName);
        final ServiceName<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexServiceName = wfDefinitionKeyIndexServiceName(wfRuntimeName);
        final ServiceName<WfDefinitionCache> wfDefinitionCacheServiceName = wfDefinitionCacheServiceName(wfRuntimeName);

        final LogIdGeneratorService wfInstanceIdGeneratorService = new LogIdGeneratorService(new WfInstanceIdReader());
        serviceContext.createService(wfInstanceIdGeneratorServiceName, wfInstanceIdGeneratorService)
            .dependency(wfInstanceLogServiceName, wfInstanceIdGeneratorService.getLogInjector())
            .install();

        final Long2LongIndexManagerService activityInstanceIndexManagerService = new Long2LongIndexManagerService(32448, 4 * 1024);
        serviceContext.createService(workflowEventIndexServiceName, activityInstanceIndexManagerService)
            .dependency(wfInstanceLogServiceName, activityInstanceIndexManagerService.getLogInjector())
            .install();

        final Bytes2LongIndexManagerService wfDefinitionKeyIndexManager = new Bytes2LongIndexManagerService(512, 32 * 1024, 256);
        serviceContext.createService(wfDefinitionKeyIndexServiceName, wfDefinitionKeyIndexManager)
            .dependency(wfInstanceLogServiceName, wfDefinitionKeyIndexManager.getLogInjector())
            .install();

        final Long2LongIndexManagerService wfDefinitionIdIndexManager = new Long2LongIndexManagerService(32448, 4 * 1024);
        serviceContext.createService(wfDefinitionIdIndexServiceName, wfDefinitionIdIndexManager)
            .dependency(wfInstanceLogServiceName, wfDefinitionIdIndexManager.getLogInjector())
            .install();

        final WfDefinitionCacheService wfDefinitionCacheService = new WfDefinitionCacheService(32, 16);
        serviceContext.createService(wfDefinitionCacheServiceName, wfDefinitionCacheService)
            .dependency(wfInstanceLogServiceName, wfDefinitionCacheService.getWfDefinitionLogInjector())
            .dependency(wfDefinitionKeyIndexServiceName, wfDefinitionCacheService.getWfDefinitionKeyIndexInjector())
            .dependency(wfDefinitionIdIndexServiceName, wfDefinitionCacheService.getWfDefinitionIdIndexInjector())
            .install();

        final WfRuntimeContextService wfRuntimeContextService = new WfRuntimeContextService(wfRuntimeId, wfRuntimeName);
        serviceContext.createService(wfRuntimeContextServiceName(wfRuntimeName), wfRuntimeContextService)
            .dependency(wfInstanceLogServiceName, wfRuntimeContextService.getLogInjector())
            .dependency(wfInstanceIdGeneratorServiceName, wfRuntimeContextService.getIdGeneratorInjector())
            .dependency(wfDefinitionCacheServiceName, wfRuntimeContextService.getWfDefinitionChacheInjector())
            .dependency(workflowEventIndexServiceName, wfRuntimeContextService.getWorkflowEventIndexInjector())
            .dependency(workerResponsePoolServiceName(WfComponent.WORKER_NAME), wfRuntimeContextService.getResponsePoolServiceInjector())
            .dependency(wfDefinitionIdIndexServiceName, wfRuntimeContextService.getWfDefinitionIdIndexInjector())
            .dependency(wfDefinitionKeyIndexServiceName, wfRuntimeContextService.getWfDefinitionKeyIndexInjector())
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
    public void registerInputLogConsumer(LogConsumer logConsumer)
    {
        inputLogConsumers.add(logConsumer);
    }

    @Override
    public List<LogConsumer> getInputLogConsumers()
    {
        return inputLogConsumers;
    }

}
