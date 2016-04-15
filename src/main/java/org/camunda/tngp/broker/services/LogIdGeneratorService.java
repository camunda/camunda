package org.camunda.tngp.broker.services;

import org.camunda.tngp.broker.servicecontainer.Injector;
import org.camunda.tngp.broker.servicecontainer.Service;
import org.camunda.tngp.broker.servicecontainer.ServiceContext;
import org.camunda.tngp.broker.servicecontainer.ServiceName;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.LastLoggedIdReader;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.log.idgenerator.spi.LogFragmentIdReader;

public class LogIdGeneratorService implements Service<IdGenerator>
{
    protected final Injector<Log> logInjector = new Injector<>();
    protected final LogFragmentIdReader logFragmentIdReader;

    protected IdGenerator idGenerator;

    public LogIdGeneratorService(final LogFragmentIdReader logFragmentIdReader)
    {
        this.logFragmentIdReader = logFragmentIdReader;
    }

    @Override
    public void start(ServiceContext serviceContext)
    {
        final LastLoggedIdReader lastLoggedIdReader = new LastLoggedIdReader();
        final Log log = logInjector.getValue();
        System.out.print(serviceContext.getName() + " recovering last id ... ");
        final long id = lastLoggedIdReader.recover(log, logFragmentIdReader);
        System.out.println(id + ".");
        idGenerator = new PrivateIdGenerator(id);
    }

    @Override
    public void stop()
    {
        // nothing to do
    }

    @Override
    public IdGenerator get()
    {
        return idGenerator;
    }

    public Injector<Log> getLogInjector()
    {
        return logInjector;
    }

}
