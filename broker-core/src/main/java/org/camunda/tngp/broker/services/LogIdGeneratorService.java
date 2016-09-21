package org.camunda.tngp.broker.services;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceContext;

public class LogIdGeneratorService implements Service<IdGenerator>
{
    protected final Injector<Log> logInjector = new Injector<>();

    protected IdGenerator idGenerator;

    @Override
    public void start(ServiceContext serviceContext)
    {
        final Log log = logInjector.getValue();
        final long lastIdUpperLimit = log.getLastPosition();
        System.out.print(serviceContext.getName() + " recovering last id ... " + lastIdUpperLimit + ".");
        idGenerator = new PrivateIdGenerator(lastIdUpperLimit);
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
