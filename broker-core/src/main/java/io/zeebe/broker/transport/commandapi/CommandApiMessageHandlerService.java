/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.transport.commandapi;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class CommandApiMessageHandlerService implements Service<CommandApiMessageHandler> {
  protected CommandApiMessageHandler service;

  protected final ServiceGroupReference<Partition> leaderPartitionsGroupReference =
      ServiceGroupReference.<Partition>create()
          .onAdd((name, partition) -> service.addPartition(partition.getLogStream()))
          .onRemove((name, partition) -> service.removePartition(partition.getLogStream()))
          .build();

  @Override
  public void start(ServiceStartContext startContext) {
    service = new CommandApiMessageHandler();
  }

  @Override
  public void stop(ServiceStopContext arg0) {
    // nothing to do
  }

  @Override
  public CommandApiMessageHandler get() {
    return service;
  }

  public ServiceGroupReference<Partition> getLeaderParitionsGroupReference() {
    return leaderPartitionsGroupReference;
  }
}
