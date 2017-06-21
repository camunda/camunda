package org.camunda.tngp.servicecontainer.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.agrona.ErrorHandler;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.servicecontainer.*;
import org.camunda.tngp.util.LangUtil;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.Actor;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;

public class ServiceContainerImpl implements Actor, ServiceContainer
{
    enum ContainerState
    {
        NEW, OPEN, CLOSING, CLOSED; // container is not reusable
    }

    private static final String NAME = "service-container-main";

    protected final Map<ServiceName<?>, ServiceController> controllersByName = new HashMap<>();
    protected final Map<ServiceName<?>, ServiceGroup> groups = new HashMap<>();
    protected final List<ServiceController> controllers = new ArrayList<>();

    private final ActorScheduler actorScheduler;
    private ActorReference actorRef;

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(1024);
    protected final Consumer<Runnable> cmdConsumer = (r) ->
    {
        r.run();
    };

    private static final ErrorHandler DEFAULT_ERROR_HANDLER = (t) ->
    {
        LangUtil.rethrowUnchecked(t);
//        t.printStackTrace();
    };

    protected ContainerState state = ContainerState.NEW;

    protected final AtomicBoolean isOpenend = new AtomicBoolean(false);
    protected ExecutorService actionsExecutor;
    protected WaitingIdleStrategy idleStrategy;

    public ServiceContainerImpl()
    {
        idleStrategy = new WaitingIdleStrategy();

        actorScheduler = ActorSchedulerImpl.newBuilder()
                .runnerIdleStrategy(idleStrategy)
                .runnerErrorHander(DEFAULT_ERROR_HANDLER)
                .build();
    }

    @Override
    public void start()
    {
        cmdQueue.add(() ->
        {
            state = ContainerState.OPEN;
        });

        idleStrategy.signalWorkAvailable();

        if (isOpenend.compareAndSet(false, true))
        {
            actorRef = actorScheduler.schedule(this);

            final AtomicInteger threadCounter = new AtomicInteger();
            actionsExecutor = Executors.newCachedThreadPool((r) -> new Thread(r, String.format("service-container-action-%d", threadCounter.getAndIncrement())));
        }
        else
        {
            final String errorMessage = String.format("Cannot start service container, is already open.");
            throw new IllegalStateException(errorMessage);
        }
    }

    @Override
    public int doWork()
    {
        int workCount = 0;

        workCount += cmdQueue.drain(cmdConsumer);

        for (int i = 0; i < controllers.size(); i++)
        {
            workCount += controllers.get(i).doWork();
        }

        return workCount;
    }


    @Override
    public String name()
    {
        return NAME;
    }

    @Override
    public <S> boolean hasService(ServiceName<S> name)
    {
        return controllersByName.containsKey(name);
    }

    @Override
    public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service)
    {
        return new ServiceBuilder<>(name, service, this);
    }

    public CompletableFuture<Void> onServiceBuilt(ServiceBuilder<?> serviceBuilder)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        executeCmd(future, () ->
        {
            if (state == ContainerState.OPEN)
            {
                final ServiceController serviceController = new ServiceController(serviceBuilder, this);

                final ServiceName<?> serviceName = serviceBuilder.getName();

                if (!controllersByName.containsKey(serviceName))
                {
                    controllersByName.put(serviceName, serviceController);
                    controllers.add(serviceController);
                    serviceController.references = createReferences(serviceController, serviceBuilder.getInjectedReferences());
                    serviceController.startAsyncInternal(future);
                }
                else
                {
                    final String errorMessage = String.format("Cannot install service with name '%s'. Service with same name already exists", serviceName);
                    future.completeExceptionally(new IllegalStateException(errorMessage));
                }
            }
            else
            {
                final String errorMessage = String.format("Cannot install new service into the contianer, state is '%s'", state);
                future.completeExceptionally(new IllegalStateException(errorMessage));
            }
        });

        future.whenComplete((r,t) ->
        {
            if (t != null)
            {
                t.printStackTrace();
            }
        });

        return future;
    }

    private void executeCmd(CompletableFuture<Void> future, Runnable r)
    {
        try
        {
            cmdQueue.add(r);
        }
        catch (Throwable t)
        {
            future.completeExceptionally(t);
        }
        finally
        {
            idleStrategy.signalWorkAvailable();
        }
    }

    @Override
    public CompletableFuture<Void> removeService(ServiceName<?> serviceName)
    {
        final CompletableFuture<Void> future = new CompletableFuture<>();

        executeCmd(future, () ->
        {
            if (state == ContainerState.OPEN || state == ContainerState.CLOSING)
            {
                final ServiceController ctrl = controllersByName.get(serviceName);

                if (ctrl != null)
                {
                    ctrl.stopAsyncInternal(future);
                }
                else
                {
                    final String errorMessage = String.format("Cannot remove service with name '%s': no such service registered.", serviceName);
                    future.completeExceptionally(new IllegalArgumentException(errorMessage));
                }
            }
            else
            {
                final String errorMessage = String.format("Cannot remove service, container is '%s'.", state);
                future.completeExceptionally(new IllegalStateException(errorMessage));
            }
        });

        future.whenComplete((r,t) ->
        {
            if (t != null)
            {
                System.err.format("Failed to remove service %s:\n", serviceName);
                t.printStackTrace();
            }
        });

        return future;
    }

    @Override
    public void close(long awaitTime, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException
    {
        final CompletableFuture<Void> containerCloseFuture = closeAsync();

        try
        {
            containerCloseFuture.get(awaitTime, timeUnit);
        }
        finally
        {
            onClosed();
        }
    }

    @SuppressWarnings("rawtypes")
    public CompletableFuture<Void> closeAsync()
    {
        final CompletableFuture<Void> containerCloseFuture = new CompletableFuture<>();

        executeCmd(containerCloseFuture, () ->
        {
            if (state == ContainerState.OPEN)
            {
                state = ContainerState.CLOSING;

                final CompletableFuture[] serviceFutures = new CompletableFuture[controllers.size()];

                for (int i = 0; i < controllers.size(); i++)
                {
                    serviceFutures[i] = removeService(controllers.get(i).name);
                }

                CompletableFuture.allOf(serviceFutures).whenComplete((r,t) ->
                {
                    containerCloseFuture.complete(null);
                });
            }
            else
            {
                final String errorMessage = String.format("Cannot close service container, container is '%s'.", state);
                containerCloseFuture.completeExceptionally(new IllegalStateException(errorMessage));
            }
        });

        return containerCloseFuture;
    }

    private void onClosed()
    {
        state = ContainerState.CLOSED;

        actorRef.close();
        actorScheduler.close();

        if (actionsExecutor != null)
        {
            actionsExecutor.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    public <S> Service<S> getService(ServiceName<?> name)
    {
        final ServiceController serviceController = controllersByName.get(name);

        if(serviceController != null && serviceController.isStarted())
        {
            return serviceController.service;
        }
        else
        {
            return null;
        }
    }

    public ServiceController getServiceController(ServiceName<?> serviceName)
    {
        return controllersByName.get(serviceName);
    }

    private List<ServiceGroupReferenceImpl> createReferences(ServiceController controller, Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences)
    {
        final List<ServiceGroupReferenceImpl> references = new ArrayList<>();

        for (Entry<ServiceName<?>, ServiceGroupReference<?>> injectedReference : injectedReferences.entrySet())
        {
            final ServiceName<?> groupName = injectedReference.getKey();
            final ServiceGroupReference<?> injector = injectedReference.getValue();

            ServiceGroup group = groups.get(groupName);
            if (group == null)
            {
                group = new ServiceGroup(groupName);
                groups.put(groupName, group);
            }

            final ServiceGroupReferenceImpl reference = new ServiceGroupReferenceImpl(controller, injector, group);

            group.addReference(reference);
            references.add(reference);
        }

        return references;
    }

    public ExecutorService getExecutor()
    {
        return actionsExecutor;
    }

    public void executeShortRunning(Runnable runnable)
    {
        actionsExecutor.execute(runnable);
    }

}
