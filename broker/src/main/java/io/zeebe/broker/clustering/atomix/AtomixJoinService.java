/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.atomix;

import io.atomix.core.Atomix;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.CompletableActorFuture;

/**
 * This should eventually become our partition service, and we will start {@link
 * io.atomix.cluster.AtomixCluster} in the {@link AtomixService}, before bootstrapping the
 * partitions here
 */
public class AtomixJoinService implements Service<Atomix> {

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  @Override
  public void start(final ServiceStartContext startContext) {
    final var started = new CompletableActorFuture<Void>();
    atomix = atomixInjector.getValue();
    atomix
        .start()
        .whenComplete(
            (nothing, error) -> {
              if (error != null) {
                started.completeExceptionally(error);
              } else {
                started.complete(null);
              }
            });

    startContext.async(started, true);
  }

  @Override
  public Atomix get() {
    return atomix;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
