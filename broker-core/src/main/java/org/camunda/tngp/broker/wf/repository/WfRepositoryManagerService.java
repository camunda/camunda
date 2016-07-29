package org.camunda.tngp.broker.wf.repository;

import static org.camunda.tngp.broker.log.LogServiceNames.*;
import static org.camunda.tngp.broker.wf.repository.WfRepositoryServiceNames.*;

import java.util.List;

import org.camunda.tngp.broker.services.Bytes2LongIndexManagerService;
import org.camunda.tngp.broker.services.HashIndexManager;
import org.camunda.tngp.broker.services.LogIdGeneratorService;
import org.camunda.tngp.broker.services.Long2LongIndexManagerService;
import org.camunda.tngp.broker.system.AbstractResourceContextProvider;
import org.camunda.tngp.broker.system.ConfigurationManager;
import org.camunda.tngp.broker.wf.cfg.WfRepositoryCfg;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.hashindex.Long2LongHashIndex;
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
        final ServiceName<HashIndexManager<Bytes2LongHashIndex>> wfDefinitionKeyIndexServiceName = wfDefinitionKeyIndexServiceName(wfRepositoryName);
        final ServiceName<HashIndexManager<Long2LongHashIndex>> wfDefinitionIdIndexServiceName = wfDefinitionIdIndexServiceName(wfRepositoryName);
        final ServiceName<WfDefinitionCacheService> wfDefinitionCacheServiceName = wfDefinitionCacheServiceName(wfRepositoryName);
        final ServiceName<WfRepositoryContext> wfRepositoryContextServiceName = wfRepositoryContextName(wfRepositoryName);

        final LogIdGeneratorService wfDefinitionIdGeneratorService = new LogIdGeneratorService(new WfDefinitionIdReader());
        serviceContext.createService(wfDefinitionIdGeneratorServiceName, wfDefinitionIdGeneratorService)
            .dependency(wfDefinitionLogServiceName, wfDefinitionIdGeneratorService.getLogInjector())
            .install();

        final Bytes2LongIndexManagerService wfDefinitionKeyIndexManager = new Bytes2LongIndexManagerService(512, 32 * 1024, 256);
        serviceContext.createService(wfDefinitionKeyIndexServiceName, wfDefinitionKeyIndexManager)
            .dependency(wfDefinitionLogServiceName, wfDefinitionKeyIndexManager.getLogInjector())
            .install();

        final Long2LongIndexManagerService wfDefinitionIdIndexManager = new Long2LongIndexManagerService(1024, 64);
        serviceContext.createService(wfDefinitionIdIndexServiceName, wfDefinitionIdIndexManager)
            .dependency(wfDefinitionLogServiceName, wfDefinitionIdIndexManager.getLogInjector())
            .install();

        final WfDefinitionCacheService wfDefinitionCacheService = new WfDefinitionCacheService(32, 16);
        serviceContext.createService(wfDefinitionCacheServiceName, wfDefinitionCacheService)
            .dependency(wfDefinitionLogServiceName, wfDefinitionCacheService.getWfDefinitionLogInjector())
            .dependency(wfDefinitionKeyIndexServiceName, wfDefinitionCacheService.getWfDefinitionKeyIndexInjector())
            .dependency(wfDefinitionIdIndexServiceName, wfDefinitionCacheService.getWfDefinitionIdIndexInjector())
            .install();

        final WfRepositoryContextService wfRepositoryContextService = new WfRepositoryContextService(wfRepositoryId, wfRepositoryName);
        serviceContext.createService(wfRepositoryContextServiceName, wfRepositoryContextService)
            .dependency(wfDefinitionLogServiceName, wfRepositoryContextService.getWfDefinitionLogInjector())
            .dependency(wfDefinitionIdGeneratorServiceName, wfRepositoryContextService.getWfDefinitionIdGeneratorInjector())
            .dependency(wfDefinitionKeyIndexServiceName, wfRepositoryContextService.getWfDefinitionKeyIndexInjector())
            .dependency(wfDefinitionIdIndexServiceName, wfRepositoryContextService.getWfDefinitionIdIndexInjector())
            .dependency(wfDefinitionCacheServiceName, wfRepositoryContextService.getWfDefinitionCacheServiceInjector())
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
