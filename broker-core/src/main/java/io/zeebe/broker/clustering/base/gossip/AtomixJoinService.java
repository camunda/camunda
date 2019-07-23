/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.gossip;

import io.atomix.core.Atomix;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;

public class AtomixJoinService implements Service<Void> {

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();

    final CompletableFuture<Void> startFuture = atomix.start();
    startContext.async(mapCompletableFuture(startFuture), true);
  }

  @Override
  public Void get() {
    return null;
  }

  private ActorFuture<Void> mapCompletableFuture(CompletableFuture<Void> atomixFuture) {
    final ActorFuture<Void> mappedActorFuture = new CompletableActorFuture<>();

    atomixFuture
        .thenAccept(mappedActorFuture::complete)
        .exceptionally(
            t -> {
              mappedActorFuture.completeExceptionally(t);
              return null;
            });
    return mappedActorFuture;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
