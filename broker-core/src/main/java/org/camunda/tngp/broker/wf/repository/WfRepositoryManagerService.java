package org.camunda.tngp.broker.wf.repository;

import static org.camunda.tngp.broker.log.LogServiceNames.logServiceName;
import static org.camunda.tngp.broker.transport.worker.WorkerServiceNames.workerResponsePoolServiceName;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.wfDefinitionIdGeneratorServiceName;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.wfRepositoryContextName;

import java.util.List;

import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.wf.WfComponent;
import org.camunda.tngp.broker.wf.cfg.WfRepositoryCfg;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.servicecontainer.ServiceName;

public class WfRepositoryManagerService
    extends
        AbstractResourceContextProvider<WfRepositoryContext>
    implements
        Service<WfRepositoryManager>,
        WfRepositoryManager
{

    protected final ConfigurationManager configurationManager;
    protected final List<WfRepositoryCfg> repositoryCfgs;

    protected ServiceContext serviceContext;

    public WfRepositoryManagerService(ConfigurationManager configurationManager)
    {
        super(WfRepositoryContext.class);
        this.configurationManager = configurationManager;
        this.repositoryCfgs = configurationManager.readList("workflow-repository", WfRepositoryCfg.class);
    }

    @Override
    public void createRepository(WfRepositoryCfg cfg)
    {
        final String wfRepositoryName = cfg.name;
        if (wfRepositoryName == null || wfRepositoryName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow repository " + wfRepositoryName + ": Configuration property 'name' cannot be null.");
        }

        final int wfRepositoryId = cfg.id;
        if (wfRepositoryId < 0 || wfRepositoryId > Short.MAX_VALUE)
        {
            throw new RuntimeException("Cannot start workflow repository " + wfRepositoryName + ": Invalid value for config property id " + wfRepositoryId + ". Value must be in range [0," + Short.MAX_VALUE + "]");
        }

        final String wfDefinitionlogName = cfg.logName;
        if (wfDefinitionlogName == null || wfDefinitionlogName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow repository " + wfRepositoryName + ": Mandatory configuration property 'wfDefinitionlogName' is not set.");
        }

        final ServiceName<Log> wfDefinitionLogServiceName = logServiceName(wfDefinitionlogName);
        final ServiceName<IdGenerator> wfDefinitionIdGeneratorServiceName = wfDefinitionIdGeneratorServiceName(wfRepositoryName);
        final ServiceName<WfRepositoryContext> wfRepositoryContextServiceName = wfRepositoryContextName(wfRepositoryName);

        final LogIdGeneratorService wfDefinitionIdGeneratorService = new LogIdGeneratorService(new WfDefinitionIdReader());
        serviceContext.createService(wfDefinitionIdGeneratorServiceName, wfDefinitionIdGeneratorService)
            .dependency(wfDefinitionLogServiceName, wfDefinitionIdGeneratorService.getLogInjector())
            .install();

        final WfRepositoryContextService wfRepositoryContextService = new WfRepositoryContextService(wfRepositoryId, wfRepositoryName);
        serviceContext.createService(wfRepositoryContextServiceName, wfRepositoryContextService)
            .dependency(wfDefinitionLogServiceName, wfRepositoryContextService.getWfDefinitionLogInjector())
            .dependency(wfDefinitionIdGeneratorServiceName, wfRepositoryContextService.getWfDefinitionIdGeneratorInjector())
            .dependency(workerResponsePoolServiceName(WfComponent.WORKER_NAME), wfRepositoryContextService.getResponsePoolServiceInjector())
            .listener(this)
            .install();
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        this.serviceContext = serviceContext;
        for (WfRepositoryCfg wfRepositoryCfg : repositoryCfgs)
        {
            createRepository(wfRepositoryCfg);
        }
    }

    @Override
    public void stop()
    {

    }

    @Override
    public WfRepositoryManager get()
    {
        return this;
    }

}
