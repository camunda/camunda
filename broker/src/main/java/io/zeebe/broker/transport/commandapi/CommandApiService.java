/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerTransport;

public class CommandApiService implements Service<CommandApiService> {

  private final ServiceGroupReference<Partition> leaderPartitionsGroupReference;
  private final Injector<ServerTransport> serverTransportInjector = new Injector<>();
  private final CommandApiMessageHandler service;
  private final PartitionAwareRequestLimiter limiter;
  private ServerOutput serverOutput;

  public CommandApiService(
      CommandApiMessageHandler commandApiMessageHandler, PartitionAwareRequestLimiter limiter) {
    this.limiter = limiter;
    this.service = commandApiMessageHandler;
    leaderPartitionsGroupReference =
        ServiceGroupReference.<Partition>create()
            .onAdd(this::addPartition)
            .onRemove(this::removePartition)
            .build();
  }

  @Override
  public void start(ServiceStartContext startContext) {
    serverOutput = serverTransportInjector.getValue().getOutput();
  }

  @Override
  public CommandApiService get() {
    return this;
  }

  public CommandResponseWriter newCommandResponseWriter(int partitionId) {
    return new CommandResponseWriterImpl(serverOutput, limiter.getLimiter(partitionId));
  }

  public ServiceGroupReference<Partition> getLeaderParitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }

  public Injector<ServerTransport> getServerTransportInjector() {
    return serverTransportInjector;
  }

  public CommandApiMessageHandler getCommandApiMessageHandler() {
    return service;
  }

  private void removePartition(ServiceName<Partition> partitionServiceName, Partition partition) {
    limiter.removePartition(partition.getPartitionId());
    service.removePartition(partition.getLogStream());
  }

  private void addPartition(ServiceName<Partition> partitionServiceName, Partition partition) {
    limiter.addPartition(partition.getPartitionId());
    service.addPartition(partition.getLogStream(), limiter.getLimiter(partition.getPartitionId()));
  }
}
