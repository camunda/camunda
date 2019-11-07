/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.impl.PositionUtil;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LogWriteBufferService implements Service<Dispatcher> {
  private final DispatcherBuilder dispatcherBuilder;
  private final LogStorage logStorage;
  private Dispatcher dispatcher;

  public LogWriteBufferService(final DispatcherBuilder builder, final LogStorage logStorage) {
    this.dispatcherBuilder = builder;
    this.logStorage = logStorage;
  }

  @Override
  public void start(final ServiceStartContext ctx) {
    ctx.run(
        () -> {
          final int partitionId = determineInitialPartitionId();

          dispatcher =
              dispatcherBuilder
                  .initialPartitionId(partitionId + 1)
                  .name(ctx.getName())
                  .actorScheduler(ctx.getScheduler())
                  .build();
        });
  }

  @Override
  public void stop(final ServiceStopContext ctx) {
    ctx.async(dispatcher.closeAsync());
  }

  @Override
  public Dispatcher get() {
    return dispatcher;
  }

  private int determineInitialPartitionId() {
    try (BufferedLogStreamReader logReader = new BufferedLogStreamReader()) {
      logReader.wrap(logStorage);

      // Get position of last entry
      final long lastPosition = logReader.seekToEnd();

      // dispatcher needs to generate positions greater than the last position
      int partitionId = 0;

      if (lastPosition > 0) {
        partitionId = PositionUtil.partitionId(lastPosition);
      }

      return partitionId;
    }
  }
}
