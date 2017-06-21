package org.camunda.tngp.broker.transport.worker;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorker;
import org.camunda.tngp.transport.requestresponse.server.AsyncRequestWorkerContext;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;

public class AsyncRequestWorkerService implements Service<AsyncRequestWorker>
{
    protected final Injector<ActorScheduler> actorSchedulerInjector = new Injector<>();
    protected final Injector<AsyncRequestWorkerContext> workerContextInjector = new Injector<>();

    protected ActorReference workerRef;

    @Override
    public void start(ServiceStartContext serviceContext)
    {
        final AsyncRequestWorkerContext workerContext = workerContextInjector.getValue();
        final ActorScheduler actorScheduler = actorSchedulerInjector.getValue();

        final AsyncRequestWorker worker = createWorker(serviceContext.getName(), workerContext);

        workerRef = actorScheduler.schedule(worker);
    }

    protected AsyncRequestWorker createWorker(String name, final AsyncRequestWorkerContext workerContext)
    {
        return new AsyncRequestWorker(name, workerContext);
    }

    @Override
    public void stop(ServiceStopContext stopContext)
    {
        workerRef.close();
    }

    @Override
    public AsyncRequestWorker get()
    {
        return null;
    }

    public Injector<ActorScheduler> getActorSchedulerInjector()
    {
        return actorSchedulerInjector;
    }

    public Injector<AsyncRequestWorkerContext> getWorkerContextInjector()
    {
        return workerContextInjector;
    }
}