package org.camunda.tngp.broker.services;

import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
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
        long lastIdUpperLimit = 0;

        final LogReader logReader = new BufferedLogReader(log);
        if (logReader.hasNext())
        {
            lastIdUpperLimit = logReader.next().getPosition() + 1;
        }

        System.out.format("%s recovered last id: %d\n", serviceContext.getName(), lastIdUpperLimit);
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
