package org.camunda.tngp.broker.services;

import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.log.idgenerator.impl.PrivateIdGenerator;
import org.camunda.tngp.logstreams.BufferedLogStreamReader;
import org.camunda.tngp.logstreams.LogStream;
import org.camunda.tngp.logstreams.LogStreamReader;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

public class LogIdGeneratorService implements Service<IdGenerator>
{
    protected final Injector<LogStream> logInjector = new Injector<>();

    protected IdGenerator idGenerator;

    @Override
    public void start(ServiceStartContext ctx)
    {
        ctx.run(() ->
        {
            final LogStream log = logInjector.getValue();
            long lastIdUpperLimit = 0;

            final LogStreamReader logReader = new BufferedLogStreamReader(log);
            if (logReader.hasNext())
            {
                lastIdUpperLimit = logReader.next().getPosition() + 1;
            }

            System.out.format("%s recovered last id: %d\n", ctx.getName(), lastIdUpperLimit);
            idGenerator = new PrivateIdGenerator(lastIdUpperLimit);
        });
    }

    @Override
    public void stop(ServiceStopContext ctx)
    {
        // nothing to do
    }

    @Override
    public IdGenerator get()
    {
        return idGenerator;
    }

    public Injector<LogStream> getLogInjector()
    {
        return logInjector;
    }

}
