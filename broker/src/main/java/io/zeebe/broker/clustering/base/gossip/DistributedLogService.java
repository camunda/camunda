/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.gossip;

import io.atomix.core.Atomix;
import io.atomix.protocols.raft.MultiRaftProtocol;
import io.zeebe.distributedlog.DistributedLogstream;
import io.zeebe.distributedlog.DistributedLogstreamBuilder;
import io.zeebe.distributedlog.DistributedLogstreamType;
import io.zeebe.distributedlog.impl.DistributedLogstreamConfig;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.CompletableFuture;

public class DistributedLogService implements Service<Void> {

  private static final MultiRaftProtocol PROTOCOL =
      MultiRaftProtocol.builder()
          // Maps partitionName to partitionId
          .withPartitioner(DistributedLogstreamName.getInstance())
          .build();
  private final Injector<Atomix> atomixInjector = new Injector<>();
  private final String primitiveName = "distributed-log";
  private Atomix atomix;

  @Override
  public void start(ServiceStartContext startContext) {
    atomix = atomixInjector.getValue();

    final CompletableFuture<DistributedLogstream> distributedLogstreamCompletableFuture =
        atomix
            .<DistributedLogstreamBuilder, DistributedLogstreamConfig, DistributedLogstream>
                primitiveBuilder(primitiveName, DistributedLogstreamType.instance())
            .withProtocol(PROTOCOL)
            .buildAsync();

    final CompletableActorFuture<Void> startFuture = new CompletableActorFuture<>();

    distributedLogstreamCompletableFuture.thenAccept(
        log -> {
          startFuture.complete(null);
        });

    startContext.async(startFuture);
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<Atomix> getAtomixInjector() {
    return atomixInjector;
  }
}
