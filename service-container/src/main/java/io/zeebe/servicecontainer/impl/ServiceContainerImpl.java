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
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceInterruptedException;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.channel.ConcurrentQueueChannel;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;
import org.slf4j.Logger;

public class ServiceContainerImpl extends Actor implements ServiceContainer {
  public static final Logger LOG = Loggers.SERVICE_CONTAINER_LOGGER;

  enum ContainerState {
    NEW,
    OPEN,
    CLOSING,
    CLOSED; // container is not reusable
  }

  private static final String NAME = "service-container-main";

  protected final ServiceDependencyResolver dependencyResolver = new ServiceDependencyResolver();
  protected final ConcurrentQueueChannel<ServiceEvent> channel =
      new ConcurrentQueueChannel<>(new ManyToOneConcurrentLinkedQueue<>());

  protected final Map<ServiceName<?>, ServiceGroup> groups = new HashMap<>();

  protected final Map<ServiceName<?>, List<ServiceController>> serviceListeners = new HashMap<>();

  protected final ActorScheduler actorScheduler;

  protected ContainerState state = ContainerState.NEW;

  protected final AtomicBoolean isOpenend = new AtomicBoolean(false);

  private final CompletableActorFuture<Void> containerCloseFuture = new CompletableActorFuture<>();

  public ServiceContainerImpl(ActorScheduler scheduler) {
    actorScheduler = scheduler;
  }

  @Override
  public void start() {
    if (isOpenend.compareAndSet(false, true)) {
      actorScheduler.submitActor(this);
      state = ContainerState.OPEN;
    } else {
      final String errorMessage = String.format("Cannot start service container, is already open.");
      throw new IllegalStateException(errorMessage);
    }
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected void onActorStarted() {
    actor.consume(channel, this::onServiceEvent);
  }

  protected void onServiceEvent() {
    final ServiceEvent serviceEvent = channel.poll();
    if (serviceEvent != null) {
      dependencyResolver.onServiceEvent(serviceEvent);
    } else {
      actor.yield();
    }
  }

  @Override
  public boolean hasService(ServiceName<?> name) {
    return dependencyResolver.getService(name) != null;
  }

  @Override
  public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service) {
    return new ServiceBuilder<>(name, service, this);
  }

  @Override
  public CompositeServiceBuilder createComposite(ServiceName<Void> name) {
    return new CompositeServiceBuilder(name, this);
  }

  public <S> ActorFuture<S> onServiceBuilt(ServiceBuilder<S> serviceBuilder) {
    final CompletableActorFuture<S> future = new CompletableActorFuture<>();

    actor.run(
        () -> {
          final ServiceName<?> serviceName = serviceBuilder.getName();
          if (state == ContainerState.OPEN) {
            final ServiceController serviceController =
                new ServiceController(serviceBuilder, this, future);

            if (!hasService(serviceController.getServiceName())) {
              actorScheduler.submitActor(serviceController);
            } else {
              final String errorMessage =
                  String.format(
                      "Cannot install service with name '%s'. Service with same name already exists",
                      serviceName);
              future.completeExceptionally(new IllegalStateException(errorMessage));
            }
          } else {
            final String errorMessage =
                String.format(
                    "Cannot install new service %s into the container, state is '%s'",
                    serviceName, state);
            future.completeExceptionally(new IllegalStateException(errorMessage));
          }

          actor.runOnCompletion(
              future,
              (r, t) -> {
                if (t != null) {
                  if (t instanceof ServiceInterruptedException) {
                    LOG.debug(
                        String.format(
                            "Service %s interrupted while building", serviceName.getName()));
                  } else {
                    LOG.error("Failed to build service", t);
                  }
                }
              });
        });

    return future;
  }

  @Override
  public ActorFuture<Void> removeService(ServiceName<?> serviceName) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();

    actor.call(
        () -> {
          if (state == ContainerState.OPEN || state == ContainerState.CLOSING) {
            final ServiceController ctrl = dependencyResolver.getService(serviceName);

            if (ctrl != null) {
              actor.runOnCompletion(
                  ctrl.remove(),
                  (v, t) -> {
                    if (t != null) {
                      future.completeExceptionally(t);
                    } else {
                      future.complete(null);
                    }
                  });
            } else {
              final String errorMessage =
                  String.format(
                      "Cannot remove service with name '%s': no such service registered.",
                      serviceName);
              future.completeExceptionally(new IllegalArgumentException(errorMessage));
            }
          } else {
            final String errorMessage =
                String.format("Cannot remove service, container is '%s'.", state);
            future.completeExceptionally(new IllegalStateException(errorMessage));
          }

          actor.runOnCompletion(
              future,
              (r, t) -> {
                if (t != null) {
                  LOG.error("Failed to remove service {}: {}", serviceName, t);
                }
              });
        });

    return future;
  }

  @Override
  public void close(long awaitTime, TimeUnit timeUnit)
      throws InterruptedException, ExecutionException, TimeoutException {
    final ActorFuture<Void> containerCloseFuture = closeAsync();

    try {
      containerCloseFuture.get(awaitTime, timeUnit);
    } catch (Exception ex) {
      Loggers.SERVICE_CONTAINER_LOGGER.debug(
          "Service container closing failed. Print dependencies.");

      final StringBuilder builder = new StringBuilder();
      dependencyResolver
          .getControllers()
          .forEach(
              (c) -> {
                builder.append("\n").append(c).append("\n\t\\");
                c.getDependencies()
                    .forEach(
                        (d) -> {
                          builder.append("\n \t-- ").append(dependencyResolver.getService(d));
                        });
              });

      Loggers.SERVICE_CONTAINER_LOGGER.debug(builder.toString());
      throw ex;
    } finally {
      onClosed();
    }
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    actor.call(
        () -> {
          if (state == ContainerState.OPEN) {
            state = ContainerState.CLOSING;

            final List<ActorFuture<Void>> serviceFutures = new ArrayList<>();
            dependencyResolver
                .getControllers()
                .forEach(
                    (c) -> {
                      serviceFutures.add(c.remove());
                    });

            actor.runOnCompletion(
                serviceFutures,
                (t) -> {
                  actor.close();
                  containerCloseFuture.complete(null);
                });
          } else {
            final String errorMessage =
                String.format("Cannot close service container, container is '%s'.", state);
            containerCloseFuture.completeExceptionally(new IllegalStateException(errorMessage));
          }
        });

    return containerCloseFuture;
  }

  private void onClosed() {
    state = ContainerState.CLOSED;
  }

  public ConcurrentQueueChannel<ServiceEvent> getChannel() {
    return channel;
  }

  public ActorScheduler getActorScheduler() {
    return actorScheduler;
  }
}
