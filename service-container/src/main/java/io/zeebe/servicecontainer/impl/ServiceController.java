/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.servicecontainer.impl;

import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceInterruptedException;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.servicecontainer.impl.ServiceEvent.ServiceEventType;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

@SuppressWarnings("rawtypes")
public class ServiceController extends Actor {
  public static final Logger LOG = Loggers.SERVICE_CONTAINER_LOGGER;
  public static final boolean IS_TRACE_ENABLED = LOG.isTraceEnabled();

  private final AwaitDependenciesStartedState awaitDependenciesStartedState =
      new AwaitDependenciesStartedState();
  private final AwaitStartState awaitStartState = new AwaitStartState();
  private final StartedState startedState = new StartedState();
  private final AwaitDependentsStopped awaitDependentsStopped = new AwaitDependentsStopped();
  private final AwaitStopState awaitStopState = new AwaitStopState();
  private final RemovedState removedState = new RemovedState();

  private final ConcurrentQueueChannel<ServiceEvent> channel =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());

  private final ServiceContainerImpl container;

  private final ServiceName name;
  private final ServiceName<?> groupName;
  private final Service service;

  private final Set<ServiceName<?>> dependencies;
  private final Map<ServiceName<?>, Collection<Injector<?>>> injectors;
  private final Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences;

  private final CompletableActorFuture<Void> stopFuture = new CompletableActorFuture<>();
  private final CompletableActorFuture startFuture;

  private List<ServiceController> resolvedDependencies;

  private StartContextImpl startContext;
  private StopContextImpl stopContext;

  private Consumer<ServiceEvent> state = awaitDependenciesStartedState;

  public ServiceController(
      ServiceBuilder<?> builder,
      ServiceContainerImpl serviceContainer,
      CompletableActorFuture startFuture) {
    this.container = serviceContainer;
    this.startFuture = startFuture;
    this.service = builder.getService();
    this.name = builder.getName();
    this.groupName = builder.getGroupName();
    this.injectors = builder.getInjectedDependencies();
    this.dependencies = builder.getDependencies();
    this.injectedReferences = builder.getInjectedReferences();
  }

  @Override
  public String getName() {
    return "service-controller";
  }

  @Override
  protected void onActorStarted() {
    actor.consume(channel, this::onServiceEvent);

    container.getChannel().add(new ServiceEvent(ServiceEventType.SERVICE_INSTALLED, this));
  }

  @Override
  protected void onActorClosed() {
    stopFuture.complete(null);
  }

  private void onServiceEvent() {
    final ServiceEvent event = channel.poll();
    if (event != null) {
      if (IS_TRACE_ENABLED) {
        LOG.trace("Got {} in state {}", event, state.getClass().getSimpleName());
      }

      state.accept(event);
    } else {
      actor.yield();
    }
  }

  private void logIgnoringEvent(final ServiceEvent event) {
    LOG.warn("Ignoring event {} in state {}", event.getType(), state.getClass().getSimpleName());
  }

  @SuppressWarnings("unchecked")
  class AwaitDependenciesStartedState implements Consumer<ServiceEvent> {
    @Override
    public void accept(ServiceEvent evt) {
      switch (evt.getType()) {
        case DEPENDENCIES_AVAILABLE:
          onDependenciesAvailable(evt);
          break;

        case DEPENDENCIES_UNAVAILABLE:
          onDependenciesUnAvailable(evt);
          break;

        case SERVICE_STOPPING:
          onStopping();
          break;

        default:
          logIgnoringEvent(evt);
          break;
      }
    }

    private void onDependenciesUnAvailable(ServiceEvent evt) {
      state = removedState;
      fireEvent(ServiceEventType.SERVICE_REMOVED);
    }

    private void onStopping() {
      state = removedState;
      fireEvent(ServiceEventType.SERVICE_REMOVED);
    }

    public void onDependenciesAvailable(ServiceEvent evt) {
      resolvedDependencies = (List<ServiceController>) evt.getPayload();

      // inject dependencies

      for (ServiceController serviceController : resolvedDependencies) {
        final Collection<Injector<?>> injectors =
            ServiceController.this.injectors.getOrDefault(
                serviceController.name, Collections.emptyList());
        for (Injector injector : injectors) {
          injector.inject(serviceController.service.get());
          injector.setInjectedServiceName(serviceController.name);
        }
      }

      // invoke start
      state = awaitStartState;

      startContext = new StartContextImpl();
      try {
        service.start(startContext);

        if (startContext.action != null) {
          actor.runBlocking(startContext.action, startContext);
        }

        if (!startContext.isAsync()) {
          fireEvent(ServiceEventType.SERVICE_STARTED);
        }
      } catch (Exception e) {
        fireEvent(ServiceEventType.SERVICE_START_FAILED, e);
      }
    }
  }

  class AwaitStartState implements Consumer<ServiceEvent> {
    boolean stopAfterStarted = false;

    @Override
    public void accept(ServiceEvent t) {
      switch (t.getType()) {
        case SERVICE_STARTED:
          onStarted();
          break;
        case SERVICE_START_FAILED:
          onStartFailed((Throwable) t.getPayload());
          break;
        case DEPENDENCIES_UNAVAILABLE:
        case DEPENDENTS_STOPPED:
        case SERVICE_STOPPING:
          if (startContext.isInterruptible()) {
            startFuture.completeExceptionally(
                new ServiceInterruptedException(String.format("Service %s was interrupted", name)));
            invokeStop(true);
          } else {
            stopAfterStarted = true;
          }
          break;

        default:
          logIgnoringEvent(t);
          break;
      }
    }

    @SuppressWarnings("unchecked")
    public void onStarted() {
      if (stopAfterStarted) {
        startFuture.completeExceptionally(
            new RuntimeException(
                String.format("Could not start service %s" + " removed while starting", name)));

        invokeStop(false);
      } else {
        state = startedState;
        startFuture.complete(getService().get());
      }
    }

    public void onStartFailed(Throwable t) {
      LOG.error("Service failed to start while in AwaitStartState", t);
      startFuture.completeExceptionally(t);
      state = awaitStopState;
      fireEvent(ServiceEventType.SERVICE_STOPPED);
    }
  }

  class StartedState implements Consumer<ServiceEvent> {
    @Override
    public void accept(ServiceEvent t) {
      switch (t.getType()) {
        case DEPENDENCIES_UNAVAILABLE:
          onDependenciesUnavailable();
          break;

        case SERVICE_STOPPING:
          onServiceStopping();
          break;

        default:
          logIgnoringEvent(t);
          break;
      }
    }

    public void onDependenciesUnavailable() {
      fireEvent(ServiceEventType.SERVICE_STOPPING);
      state = awaitDependentsStopped;
    }

    public void onServiceStopping() {
      state = awaitDependentsStopped;
    }
  }

  class AwaitDependentsStopped implements Consumer<ServiceEvent> {
    @Override
    public void accept(ServiceEvent t) {
      if (t.getType() == ServiceEventType.DEPENDENTS_STOPPED) {
        invokeStop(false);
      }
    }
  }

  class AwaitStopState implements Consumer<ServiceEvent> {
    @Override
    public void accept(ServiceEvent t) {
      if (t.getType() == ServiceEventType.SERVICE_STOPPED) {
        injectors.values().stream().flatMap(Collection::stream).forEach(i -> i.uninject());

        fireEvent(ServiceEventType.SERVICE_REMOVED);

        state = removedState;
      }
    }
  }

  class RemovedState implements Consumer<ServiceEvent> {
    @Override
    public void accept(ServiceEvent t) {
      if (t.getType() == ServiceEventType.SERVICE_REMOVED) {
        actor.close();
      }
    }
  }

  private void invokeStop(boolean interrupted) {
    state = awaitStopState;

    if (startContext != null) {
      startContext.invalidate();
    }

    stopContext = new StopContextImpl();
    stopContext.wasInterrupted = interrupted;

    try {
      service.stop(stopContext);

      if (stopContext.action != null) {
        actor.runBlocking(stopContext.action, stopContext);
      }

      if (!stopContext.isAsync()) {
        fireEvent(ServiceEventType.SERVICE_STOPPED);
      }
    } catch (Throwable t) {
      LOG.error("Exception while stopping service {}: {}", this, t);
      fireEvent(ServiceEventType.SERVICE_STOPPED);
    }
  }

  class StartContextImpl implements ServiceStartContext, Consumer<Throwable> {
    final Set<ServiceName<?>> dependentServices = new HashSet<>();

    boolean isValid = true;
    boolean isAsync = false;
    boolean isInterruptible = false;
    boolean stopOnCompletion = false;
    Runnable action;

    public void invalidate() {
      isValid = false;
      startContext = null;
    }

    @Override
    public ServiceName<?> getServiceName() {
      return name;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> S getService(ServiceName<S> name) {
      validCheck();
      dependencyCheck(name);
      return (S) resolvedDependencies.stream().filter((c) -> c.name.equals(name)).findFirst();
    }

    @Override
    public <S> S getService(String name, Class<S> type) {
      validCheck();

      return getService(ServiceName.newServiceName(name, type));
    }

    @Override
    public String getName() {
      return name.getName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service) {
      validCheck();

      dependentServices.add(name);

      return new ServiceBuilder<>(name, service, container).dependency(ServiceController.this.name);
    }

    @Override
    public CompositeServiceBuilder createComposite(ServiceName<Void> name) {
      validCheck();

      dependentServices.add(name);

      return new CompositeServiceBuilder(name, container, ServiceController.this.name);
    }

    @Override
    public <S> ActorFuture<Void> removeService(ServiceName<S> name) {
      validCheck();

      if (!dependentServices.contains(name)) {
        final Optional<ServiceController> contoller =
            resolvedDependencies.stream().filter((c) -> c.name.equals(name)).findFirst();

        if (!contoller.isPresent()) {
          final String errorMessage =
              String.format(
                  "Cannot remove service '%s' from context '%s'. Can only remove dependencies and services started through this context.",
                  name, ServiceController.this.name);
          return CompletableActorFuture.completedExceptionally(
              new IllegalArgumentException(errorMessage));
        }
      }

      return container.removeService(name);
    }

    @Override
    public void async(ActorFuture<?> future, boolean interruptible) {
      validCheck();
      notAsyncCheck();
      isAsync = true;
      isInterruptible = interruptible;
      actor.runOnCompletion(future, (v, t) -> accept(t));
    }

    @Override
    public void run(Runnable action) {
      validCheck();
      notAsyncCheck();
      isAsync = true;
      this.action = action;
    }

    void validCheck() {
      if (!isValid) {
        throw new IllegalStateException("Service Context is invalid");
      }
    }

    void dependencyCheck(ServiceName<?> name) {
      if (!dependencies.contains(name)) {
        final String errorMessage =
            String.format(
                "Cannot get service '%s' from context '%s'. Requested Service is not a dependency.",
                name, ServiceController.this.name);
        throw new IllegalArgumentException(errorMessage);
      }
    }

    boolean isAsync() {
      validCheck();
      return isAsync;
    }

    boolean isInterruptible() {
      validCheck();
      return isInterruptible;
    }

    private void notAsyncCheck() {
      if (isAsync) {
        throw new IllegalStateException(
            "Context is already async. Cannnot call asyc() more than once.");
      }
    }

    @Override
    public void accept(Throwable u) {
      if (u == null) {
        fireEvent(ServiceEventType.SERVICE_STARTED);
      } else {
        fireEvent(ServiceEventType.SERVICE_START_FAILED, u);
      }
    }

    @Override
    public ActorScheduler getScheduler() {
      validCheck();
      return container.getActorScheduler();
    }

    @Override
    public <S> boolean hasService(ServiceName<S> name) {
      validCheck();
      return container.hasService(name);
    }
  }

  class StopContextImpl implements ServiceStopContext, Consumer<Throwable> {
    boolean isValid = true;
    boolean isAsync = false;
    Runnable action;
    boolean wasInterrupted = false;

    @Override
    public boolean wasInterrupted() {
      return wasInterrupted;
    }

    @Override
    public void async(ActorFuture<?> future) {
      validCheck();
      notAsyncCheck();
      isAsync = true;
      actor.runOnCompletion(future, (v, t) -> accept(t));
    }

    @Override
    public void run(Runnable action) {
      validCheck();
      notAsyncCheck();
      isAsync = true;
      this.action = action;
    }

    void validCheck() {
      if (!isValid) {
        throw new IllegalStateException("Service Context is invalid");
      }
    }

    boolean isAsync() {
      validCheck();
      return isAsync;
    }

    private void notAsyncCheck() {
      if (isAsync) {
        throw new IllegalStateException(
            "Context is already async. Cannnot call asyc() more than once.");
      }
    }

    @Override
    public void accept(Throwable u) {
      fireEvent(ServiceEventType.SERVICE_STOPPED);
    }
  }

  // API & Cmds ////////////////////////////////////////////////

  @Override
  public String toString() {
    return String.format("%s in %s", name, state.getClass().getSimpleName());
  }

  private void fireEvent(ServiceEventType evtType) {
    fireEvent(evtType, null);
  }

  private void fireEvent(ServiceEventType evtType, Object payload) {
    final ServiceEvent event = new ServiceEvent(evtType, this, payload);

    channel.add(event);
    container.getChannel().add(event);
  }

  public ConcurrentQueueChannel<ServiceEvent> getChannel() {
    return channel;
  }

  public Set<ServiceName<?>> getDependencies() {
    return dependencies;
  }

  public ActorFuture<Void> remove() {
    actor.run(
        () -> {
          fireEvent(ServiceEventType.SERVICE_STOPPING);
        });

    return stopFuture;
  }

  public ServiceName<?> getGroupName() {
    return groupName;
  }

  public ServiceName<?> getServiceName() {
    return name;
  }

  public Map<ServiceName<?>, ServiceGroupReference<?>> getInjectedReferences() {
    return injectedReferences;
  }

  public Service getService() {
    return service;
  }

  public void addReferencedValue(ServiceGroupReference ref, ServiceName name, Object value) {
    actor.call(
        () -> {
          invoke(ref.getAddHandler(), name, value);
        });
  }

  public void removeReferencedValue(ServiceGroupReference ref, ServiceName name, Object value) {
    actor.call(
        () -> {
          invoke(ref.getRemoveHandler(), name, value);
        });
  }

  @SuppressWarnings("unchecked")
  private static <S> void invoke(BiConsumer consumer, ServiceName name, Object value) {
    consumer.accept(name, value);
  }
}
