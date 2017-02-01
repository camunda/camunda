package org.camunda.tngp.servicecontainer.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceGroupReference;
import org.camunda.tngp.servicecontainer.ServiceName;
import org.camunda.tngp.servicecontainer.ServiceStartContext;
import org.camunda.tngp.servicecontainer.ServiceStopContext;

@SuppressWarnings("rawtypes")
public class ServiceController
{
    /** The operation currently being performed by the service controller */
    public enum ServiceOperation
    {
        /**
         * Service is being installed into the container
         */
        INSTALLING,
        /**
         * Service is being removed from the container
         */
        REMOVING,
    }

    private static final String AWAIT_ASYNC_START = "AwaitAsyncStart";
    private static final String AWAIT_DEPENDENTS_STOP = "AwaitDependentsStop";
    private static final String AWAIT_ASYNC_STOP = "AwaitAsyncStop";

    protected final StoppedState stoppedState = new StoppedState();
    protected final ResolvingState resolvingState = new ResolvingState();
    protected final AwaitingDependenciesState awaitDependenciesState = new AwaitingDependenciesState();
    protected final InjectDependenciesState injectDependenciesState = new InjectDependenciesState();
    protected final InvokeStartState invokeStartState = new InvokeStartState();
    protected final WaitingState awaitAsyncStartState = new WaitingState(AWAIT_ASYNC_START);
    protected final UpdateReferencesState updateReferencesState = new UpdateReferencesState();
    protected final StartedState startedState = new StartedState();
    protected final RemoveReferencesState removeReferencesState = new RemoveReferencesState();
    protected final StopDependentsState stopDependentsState = new StopDependentsState();
    protected final WaitingState awaitDependentsStop = new WaitingState(AWAIT_DEPENDENTS_STOP);
    protected final InvokeStopState invokeStopState = new InvokeStopState();
    protected final WaitingState awaitAsyncStopState = new WaitingState(AWAIT_ASYNC_STOP);
    protected final UninjectDependenciesState uninjectDependenciesState = new UninjectDependenciesState();
    protected final UnresolveState unresolveState = new UnresolveState();

    protected ServiceOperation operation = null;
    protected ServiceState state = stoppedState;
    /** when a controller stops, it always goes first into the StopDependents -&gt; AwaitDependentsStop
     * sequence. Then it performs the rest of the stop states, depending on how far it progressed
     * through it's lifecycle.
     */
    protected ServiceState firstStopState = null;

    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue = new ManyToOneConcurrentArrayQueue<>(8);
    protected final Consumer<Runnable> cmdHandler = (r) ->
    {
        r.run();
    };

    protected final ServiceContainerImpl container;

    protected final ServiceName name;
    protected final ServiceName<?> groupName;
    protected final Service service;
    /** this service's dependencies */
    protected final Set<ServiceName<?>> dependencies;
    protected final Map<ServiceName<?>, Collection<Injector<?>>> injectors;

    /** this service's unresolved dependencies */
    protected final List<ServiceName<?>> unresolvedDependencies = new ArrayList<>();
    /** resolved services on which this service depends */
    protected final List<ServiceController> resolvedDependencies = new ArrayList<>();
    /** resolved services depending on this service */
    protected final List<ServiceController> resolvedDependents = new ArrayList<>();
    /** this service's resolved references */
    protected List<ServiceGroupReferenceImpl> references;

    protected StartContextImpl startContext;
    protected StopContextImpl stopContext;
    protected final List<CompletableFuture<Void>> stopFutures = new ArrayList<>();
    protected CompletableFuture<Void> startFuture;
    /** captures exception caught during installation operation of the service*/
    protected Throwable installException;

    public ServiceController(ServiceBuilder<?> builder, ServiceContainerImpl serviceContainer)
    {
        this.container = serviceContainer;
        this.service = builder.getService();
        this.name = builder.getName();
        groupName = builder.getGroupName();
        this.injectors = builder.getInjectedDependencies();
        this.dependencies = builder.getDependencies();
        this.unresolvedDependencies.addAll(dependencies);
    }

    public int doWork()
    {
        int workCount = 0;

        try
        {
            workCount += cmdQueue.drain(cmdHandler);
            workCount += state.doWork();
        }
        catch(Throwable t)
        {
            onThrowable(t);

            if (operation == ServiceOperation.INSTALLING)
            {
                stopAsyncInternal(new CompletableFuture<Void>());
            }
            else
            {
                state = stoppedState;
            }

            ++workCount;
        }

        return workCount;
    }

    private void onThrowable(Throwable t)
    {
        t.printStackTrace();

        if (operation == ServiceOperation.INSTALLING)
        {
            this.installException = t;
        }
    }


    interface ServiceState
    {
        int doWork() throws Exception;

        default String getName()
        {
            return getClass().getSimpleName().replaceFirst("State", "");
        }
    }

    class ResolvingState implements ServiceState
    {
        @Override
        public int doWork()
        {
            int workCount = 0;

            final Iterator<ServiceName<?>> unresolvedIterator = unresolvedDependencies.iterator();

            while (unresolvedIterator.hasNext())
            {
                final ServiceName<?> serviceName = unresolvedIterator.next();
                final ServiceController controller = container.getServiceController(serviceName);

                if (controller != null)
                {
                   unresolvedIterator.remove();
                   resolvedDependencies.add(controller);
                   controller.resolvedDependents.add(ServiceController.this);
                   ++workCount;
                }
            }

            if (unresolvedDependencies.isEmpty())
            {
                state = awaitDependenciesState;
                ++workCount;
            }

            return workCount;
        }
    }

    class AwaitingDependenciesState implements ServiceState
    {
        @Override
        public int doWork()
        {
            int workCount = 0;

            boolean allAvailable = true;

            for (int i = 0; i < resolvedDependencies.size() && allAvailable; i++)
            {
                allAvailable &= resolvedDependencies.get(i).isStarted();
            }

            if (allAvailable)
            {
                state = injectDependenciesState;
                ++workCount;
            }

            return workCount;
        }

    }

    class InjectDependenciesState implements ServiceState
    {
        @Override
        @SuppressWarnings("unchecked")
        public int doWork()
        {
            for (Entry<ServiceName<?>, Collection<Injector<?>>> injectedDep : injectors.entrySet())
            {
                final ServiceName<?> serviceName = injectedDep.getKey();
                final Service injectedService = container.getService(serviceName);

                for (Injector injector : injectedDep.getValue())
                {
                    injector.inject(injectedService.get());
                }
            }

            state = invokeStartState;

            return 1;
        }
    }

    class InvokeStartState implements ServiceState
    {
        @Override
        public int doWork() throws Exception
        {
            startContext = new StartContextImpl();

            service.start(startContext);

            if (startContext.action != null)
            {
                final Runnable action = startContext.action;
                final CompletableFuture<Void> future = new CompletableFuture<>();
                future.whenComplete(startContext);

                container.executeShortRunning(()->
                {
                    try
                    {
                        action.run();
                        future.complete(null);
                    }
                    catch(Throwable t)
                    {
                        future.completeExceptionally(t);
                    }
                });
            }


            if (!startContext.isAsync())
            {
                state = updateReferencesState;
            }
            else
            {
                state = awaitAsyncStartState;
            }

            return 1;
        }
    }

    class UpdateReferencesState implements ServiceState
    {
        @Override
        public int doWork()
        {
            for (ServiceGroupReferenceImpl reference : references)
            {
                reference.injectInitialValues();
            }

            if (groupName != null)
            {
                ServiceGroup serviceGroup = container.groups.get(groupName);
                if (serviceGroup == null)
                {
                    serviceGroup = new ServiceGroup(groupName);
                    container.groups.put(groupName, serviceGroup);
                }
                serviceGroup.addService(ServiceController.this);
            }

            state = startedState;

            return 1;
        }
    }

    class RemoveReferencesState implements ServiceState
    {
        @Override
        public int doWork()
        {
            if (groupName != null)
            {
                final ServiceGroup serviceGroup = container.groups.get(groupName);
                if (serviceGroup != null)
                {
                    serviceGroup.removeService(ServiceController.this);
                }
            }

            state = invokeStopState;

            return 1;
        }
    }


    class StopDependentsState implements ServiceState
    {
        @Override
        public int doWork()
        {
            final CompletableFuture[] stopFutures = new CompletableFuture[resolvedDependents.size()];

            for (int i = 0; i < resolvedDependents.size(); i++)
            {
                final ServiceController serviceController = resolvedDependents.get(i);
                final CompletableFuture<Void> future = new CompletableFuture<>();
                serviceController.stopAsyncInternal(future);

                stopFutures[i] = future;
            }

            CompletableFuture.allOf(stopFutures)
            .whenComplete((r,t) ->
            {
                onDependentsStopped();
            });

            state = awaitDependentsStop;

            return 1;
        }
    }

    class InvokeStopState implements ServiceState
    {
        @Override
        public int doWork()
        {
            startContext.invalidate();
            stopContext = new StopContextImpl();

            try
            {
                service.stop(stopContext);

                if (stopContext.action != null)
                {
                    final Runnable action = stopContext.action;
                    final CompletableFuture<Void> future = new CompletableFuture<>();
                    future.whenComplete(stopContext);

                    // wrap runnable
                    container.executeShortRunning(()->
                    {
                        try
                        {
                            action.run();
                            future.complete(null);
                        }
                        catch(Throwable t)
                        {
                            t.printStackTrace();
                            future.completeExceptionally(t);
                        }
                    });
                }

                if (!stopContext.isAsync())
                {
                    state = uninjectDependenciesState;
                }
                else
                {
                    state = awaitAsyncStopState;
                }
            }
            catch (Throwable t)
            {
                state = uninjectDependenciesState;
            }

            return 1;
        }
    }

    class UninjectDependenciesState implements ServiceState
    {
        @Override
        public int doWork()
        {
            if (startContext != null)
            {
                startContext.invalidate();
            }

            injectors.values().stream().flatMap(Collection::stream).forEach(injector -> injector.uninject());

            for (ServiceGroupReferenceImpl reference : references)
            {
                reference.uninject();
            }

            state = unresolveState;

            return 1;
        }
    }

    class UnresolveState implements ServiceState
    {
        @Override
        public int doWork()
        {
            for (ServiceController dependency : resolvedDependencies)
            {
                dependency.resolvedDependents.remove(ServiceController.this);
            }
            resolvedDependencies.clear();
            unresolvedDependencies.clear();
            unresolvedDependencies.addAll(dependencies);
            resolvedDependents.clear();
            state = stoppedState;

            return 1;
        }
    }

    class WaitingState implements ServiceState
    {
        protected final String name;

        public WaitingState(String name)
        {
            this.name = name;
        }

        @Override
        public int doWork()
        {
            return 0;
        }

        @Override
        public String getName()
        {
            return name;
        }
    }

    class StartedState implements ServiceState
    {
        @Override
        public int doWork()
        {
            int workCount = 0;

            if (operation == ServiceOperation.INSTALLING)
            {
                startFuture.complete(null);
                startFuture = null;
                ++workCount;
            }

            operation = null;

            return workCount;
        }
    }

    class StoppedState implements ServiceState
    {
        @Override
        public int doWork()
        {
            for (int i = 0; i < stopFutures.size(); i++)
            {
                stopFutures.get(i).complete(null);
            }
            stopFutures.clear();

            if (operation == ServiceOperation.INSTALLING)
            {
                final String exceptionMsg = String.format("Could not install service '%s' into the container.", name);
                final RuntimeException exception = new RuntimeException(exceptionMsg, installException);
                startFuture.completeExceptionally(exception);
                startFuture = null;
            }

            container.controllers.remove(ServiceController.this);
            container.controllersByName.remove(name);

            for (ServiceGroupReferenceImpl reference : references)
            {
                reference.remove();
            }

            operation = null;

            return 1;
        }
    }

    class StartContextImpl implements ServiceStartContext, BiConsumer<Object, Throwable>
    {
        boolean isValid = true;
        boolean isAsync = false;
        boolean stopOnCompletion = false;
        Runnable action;

        public void invalidate()
        {
            isValid = false;
            startContext = null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> S getService(ServiceName<S> name)
        {
            validCheck();
            dependencyCheck(name);
            return (S) container.getService(name).get();
        }

        @Override
        public <S> S getService(String name, Class<S> type)
        {
            validCheck();

            return getService(ServiceName.newServiceName(name, type));
        }

        @Override
        public String getName()
        {
            return name.getName();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service)
        {
            validCheck();

            return new ServiceBuilder<>(name, service, container)
                    .dependency(ServiceController.this.name);
        }

        @Override
        public <S> CompletableFuture<Void> removeService(ServiceName<S> name)
        {
            validCheck();

            final ServiceController serviceController = container.getServiceController(name);
            if (serviceController == null)
            {
                final String errorMessage = String.format("Cannot remove service '%s' from context '%s'. Service not found.", name, ServiceController.this.name);
                throw new IllegalArgumentException(errorMessage);
            }

            if (!serviceController.hasDependency(ServiceController.this.name))
            {
                final String errorMessage = String.format("Cannot remove service '%s' from context '%s'. The context is not a dependency of the service.", name, ServiceController.this.name);
                throw new IllegalArgumentException(errorMessage);
            }

            return container.removeService(name);
        }

        @Override
        public CompletableFuture<Void> async()
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            final CompletableFuture<Void> future = new CompletableFuture<>();
            future.whenComplete(this);
            return future;
        }

        @Override
        public void async(CompletableFuture<?> future)
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            future.whenComplete(this);
        }

        @Override
        public void run(Runnable action)
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            this.action = action;
        }

        void validCheck()
        {
            if(!isValid)
            {
                throw new IllegalStateException("Service Context is invalid");
            }
        }

        void dependencyCheck(ServiceName<?> name)
        {
            if (!dependencies.contains(name))
            {
                final String errorMessage = String.format("Cannot get service '%s' from context '%s'. Requested Service is not a dependency.", name, ServiceController.this.name);
                throw new IllegalArgumentException(errorMessage);
            }
        }

        boolean isAsync()
        {
            validCheck();
            return isAsync;
        }

        private void notAsyncCheck()
        {
            if (isAsync)
            {
               throw new IllegalStateException("Context is already async. Cannnot call asyc() more than once.");
            }
        }

        @Override
        public void accept(Object t, Throwable u)
        {
            if (stopOnCompletion)
            {
                firstStopState = invokeStopState;
            }
            else
            {
                if (u == null)
                {
                    onAsyncStartCompleted();
                }
                else
                {
                    onAsyncStartFailed(u);
                }
            }
        }

    }

    class StopContextImpl implements ServiceStopContext, BiConsumer<Object, Throwable>
    {
        boolean isValid = true;
        boolean isAsync = false;
        Runnable action;

        protected void invalidate()
        {
            isValid = false;
            stopContext = null;
        }

        @Override
        public CompletableFuture<Void> async()
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            final CompletableFuture<Void> f = new CompletableFuture<>();
            f.whenComplete(this);
            return f;
        }

        @Override
        public void async(CompletableFuture<?> future)
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            future.whenComplete(this);
        }

        @Override
        public void run(Runnable action)
        {
            validCheck();
            notAsyncCheck();
            isAsync = true;
            this.action = action;
        }

        void validCheck()
        {
            if(!isValid)
            {
                throw new IllegalStateException("Service Context is invalid");
            }
        }

        void dependencyCheck(ServiceName<?> name)
        {
            if (!dependencies.contains(name))
            {
                final String errorMessage = String.format("Cannot get service '%s' from context '%s'. Requested Service is not a dependency.", name, ServiceController.this.name);
                throw new IllegalArgumentException(errorMessage);
            }
        }

        boolean isAsync()
        {
            validCheck();
            return isAsync;
        }

        private void notAsyncCheck()
        {
            if (isAsync)
            {
               throw new IllegalStateException("Context is already async. Cannnot call asyc() more than once.");
            }
        }

        @Override
        public void accept(Object t, Throwable u)
        {
            if (u != null)
            {
                u.printStackTrace();
            }

            onAsyncStopCompleted();
        }
    }

    // API & Cmds ////////////////////////////////////////////////

    /*
     * must only be called from container cmd
     */
    public void startAsyncInternal(CompletableFuture<Void> future)
    {
        if (state == stoppedState)
        {
            operation = ServiceOperation.INSTALLING;
            startFuture = future;
            state = resolvingState;
        }
        else
        {
            final String errorMessage = String.format("Cannot start service '%s': not in state 'stopped'.", name);
            future.completeExceptionally(new IllegalStateException(errorMessage));
        }
    }

    /*
     * must only be called from container cmd
     */
    public void stopAsyncInternal(CompletableFuture<Void> future)
    {
        stopFutures.add(future);
        container.controllersByName.remove(this);

        if (operation == null)
        {
            operation = ServiceOperation.REMOVING;
        }

        if (firstStopState == null)
        {
            if (state == resolvingState || state == awaitDependenciesState || state == injectDependenciesState)
            {
                firstStopState = unresolveState;
            }
            else if (state == invokeStartState)
            {
                firstStopState = uninjectDependenciesState;
            }
            else if (state == awaitAsyncStartState)
            {
                startContext.stopOnCompletion = true;
            }
            else if (state == updateReferencesState)
            {
                firstStopState = invokeStopState;
            }
            else if (state == startedState)
            {
                firstStopState = removeReferencesState;
            }

            state = stopDependentsState;
        }
    }

    protected void onAsyncStartCompleted()
    {
        cmdQueue.add(() ->
        {
            if (state == awaitAsyncStartState)
            {
                state = updateReferencesState;
            }
        });
        container.idleStrategy.signalWorkAvailable();
    }

    protected void onAsyncStartFailed(Throwable t)
    {
        cmdQueue.add(() ->
        {
            onThrowable(t);

            if (state == awaitAsyncStartState)
            {
                container.controllersByName.remove(ServiceController.this);
                state = stopDependentsState;
                firstStopState = uninjectDependenciesState;
            }
        });
        container.idleStrategy.signalWorkAvailable();
    }

    protected void onAsyncStopCompleted()
    {
        cmdQueue.add(() ->
        {
            if (state == awaitAsyncStopState)
            {
                state = uninjectDependenciesState;
            }
        });
        container.idleStrategy.signalWorkAvailable();
    }

    public void onDependentsStopped()
    {
        cmdQueue.add(() ->
        {
            if (state == awaitDependentsStop)
            {
                state = firstStopState;
            }
        });
    }

    @SuppressWarnings("unchecked")
    public void onReferencedServiceStart(ServiceGroupReferenceImpl reference, ServiceController controller)
    {
        cmdQueue.add(() ->
        {
           if (isStarted())
           {
               final ServiceGroupReference injector = reference.getInjector();
               final Object value = controller.service.get();

               injector.addValue(controller.name, value);
           }
        });
    }

    @SuppressWarnings("unchecked")
    public void onReferencedServiceStop(ServiceGroupReferenceImpl reference, ServiceController controller)
    {
        cmdQueue.add(() ->
        {
            if (isStarted())
            {
                final ServiceGroupReference injector = reference.getInjector();
                final Object value = controller.service.get();

                injector.removeValue(controller.name, value);
            }
        });
    }

    public boolean isStarted()
    {
        return state == startedState;
    }

    public boolean hasDependency(ServiceName<?> name)
    {
        return dependencies.contains(name);
    }

    @Override
    public String toString()
    {
        return String.format("[%s (%s, %s)]", name, operation, state.getName());
    }

}
