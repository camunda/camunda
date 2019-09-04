/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl.service;

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.Loggers;
import io.zeebe.util.retry.AbortableRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.Actor;
import java.io.IOException;
import java.util.function.Function;
import org.slf4j.Logger;

public class FsLogStorageService extends Actor implements Service<LogStorage> {
  private static final Logger LOG = Loggers.IO_LOGGER;

  private final FsLogStorageConfiguration config;
  private final int partitionId;
  private final Function<FsLogStorage, FsLogStorage> logStorageStubber; // for testing only

  private boolean closeRequested;

  private FsLogStorage logStorage;

  public FsLogStorageService(
      final FsLogStorageConfiguration config,
      final int partitionId,
      final Function<FsLogStorage, FsLogStorage> logStorageStubber) {
    this.config = config;
    this.partitionId = partitionId;
    this.logStorageStubber = logStorageStubber;
    closeRequested = false;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    logStorage = logStorageStubber.apply(new FsLogStorage(config));
    startContext.async(startContext.getScheduler().submitActor(this));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    closeRequested = true;
    stopContext.async(actor.close());
  }

  @Override
  public LogStorage get() {
    return logStorage;
  }

  @Override
  protected void onActorStarting() {
    final RetryStrategy abortableRetryStrategy = new AbortableRetryStrategy(actor);

    abortableRetryStrategy.runWithRetry(
        () -> {
          try {
            logStorage.open();
            return true;
          } catch (IOException e) {
            // retry until success
            LOG.error("Failed to open logstorage for partition {}, try again.", partitionId, e);
            return false;
          }
        },
        () -> closeRequested);
  }

  @Override
  protected void onActorClosing() {
    logStorage.close();
  }
}
