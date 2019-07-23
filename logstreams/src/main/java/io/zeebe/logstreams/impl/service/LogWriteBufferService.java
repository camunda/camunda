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
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;

public class LogWriteBufferService implements Service<Dispatcher> {
  private final Injector<LogStorage> logStorageInjector = new Injector<>();

  protected DispatcherBuilder dispatcherBuilder;
  protected Dispatcher dispatcher;

  public LogWriteBufferService(DispatcherBuilder builder) {
    this.dispatcherBuilder = builder;
  }

  @Override
  public void start(ServiceStartContext ctx) {
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
  public void stop(ServiceStopContext ctx) {
    ctx.async(dispatcher.closeAsync());
  }

  @Override
  public Dispatcher get() {
    return dispatcher;
  }

  private int determineInitialPartitionId() {
    final LogStorage logStorage = logStorageInjector.getValue();

    try (BufferedLogStreamReader logReader = new BufferedLogStreamReader()) {
      logReader.wrap(logStorage);

      long lastPosition = 0;

      // Get position of last entry
      logReader.seekToLastEvent();
      if (logReader.hasNext()) {
        final LoggedEvent lastEntry = logReader.next();
        lastPosition = lastEntry.getPosition();
      }

      // dispatcher needs to generate positions greater than the last position
      int partitionId = 0;

      if (lastPosition > 0) {
        partitionId = PositionUtil.partitionId(lastPosition);
      }

      return partitionId;
    }
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }
}
