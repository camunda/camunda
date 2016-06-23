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

        final String wfTypelogName = cfg.logName;
        if (wfTypelogName == null || wfTypelogName.isEmpty())
        {
            throw new RuntimeException("Cannot start workflow repository " + wfRepositoryName + ": Mandatory configuration property 'wfTypelogName' is not set.");
        }

        final ServiceName<Log> wfTypeLogServiceName = logServiceName(wfTypelogName);
        final ServiceName<IdGenerator> wfTypeIdGeneratorServiceName = wfTypeIdGeneratorServiceName(wfRepositoryName);
        final ServiceName<HashIndexManager<Bytes2LongHashIndex>> wfTypeKeyIndexServiceName = wfTypeKeyIndexServiceName(wfRepositoryName);
        final ServiceName<HashIndexManager<Long2LongHashIndex>> wfTypeIdIndexServiceName = wfTypeIdIndexServiceName(wfRepositoryName);
        final ServiceName<WfTypeCacheService> wfTypeCacheServiceName = wfTypeCacheServiceName(wfRepositoryName);
        final ServiceName<WfRepositoryContext> wfRepositoryContextServiceName = wfRepositoryContextName(wfRepositoryName);

        final LogIdGeneratorService wfTypeIdGeneratorService = new LogIdGeneratorService(new WfTypeIdReader());
        serviceContext.createService(wfTypeIdGeneratorServiceName, wfTypeIdGeneratorService)
            .dependency(wfTypeLogServiceName, wfTypeIdGeneratorService.getLogInjector())
            .install();

        final Bytes2LongIndexManagerService wfTypeKeyIndexManager = new Bytes2LongIndexManagerService(512, 32 * 1024, 256);
        serviceContext.createService(wfTypeKeyIndexServiceName, wfTypeKeyIndexManager)
            .dependency(wfTypeLogServiceName, wfTypeKeyIndexManager.getLogInjector())
            .install();

        final Long2LongIndexManagerService wfTypeIdIndexManager = new Long2LongIndexManagerService(1024, 64);
        serviceContext.createService(wfTypeIdIndexServiceName, wfTypeIdIndexManager)
            .dependency(wfTypeLogServiceName, wfTypeIdIndexManager.getLogInjector())
            .install();

        final WfTypeCacheService wfTypeCacheService = new WfTypeCacheService(32, 16);
        serviceContext.createService(wfTypeCacheServiceName, wfTypeCacheService)
            .dependency(wfTypeLogServiceName, wfTypeKeyIndexManager.getLogInjector())
            .dependency(wfTypeKeyIndexServiceName, wfTypeCacheService.getWfTypeKeyIndexInjector())
            .dependency(wfTypeIdIndexServiceName, wfTypeCacheService.getWfTypeIdIndexInjector())
            .install();

        final WfRepositoryContextService wfRepositoryContextService = new WfRepositoryContextService(wfRepositoryId, wfRepositoryName);
        serviceContext.createService(wfRepositoryContextServiceName, wfRepositoryContextService)
            .dependency(wfTypeLogServiceName, wfRepositoryContextService.getWfTypeLogInjector())
            .dependency(wfTypeIdGeneratorServiceName, wfRepositoryContextService.getWfTypeIdGeneratorInjector())
            .dependency(wfTypeKeyIndexServiceName, wfRepositoryContextService.getWfTypeKeyIndexInjector())
            .dependency(wfTypeIdIndexServiceName, wfRepositoryContextService.getWfTypeIdIndexInjector())
            .dependency(wfTypeCacheServiceName, wfRepositoryContextService.getWfTypeCacheServiceInjector())
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
