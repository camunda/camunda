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
import java.io.IOException;
import java.util.function.Function;

public class FsLogStorageService implements Service<LogStorage> {
  private final FsLogStorageConfiguration config;
  private final int partitionId;
  private final Function<FsLogStorage, FsLogStorage> logStorageStubber; // for testing only

  private FsLogStorage logStorage;

  public FsLogStorageService(
      final FsLogStorageConfiguration config,
      final int partitionId,
      final Function<FsLogStorage, FsLogStorage> logStorageStubber) {
    this.config = config;
    this.partitionId = partitionId;
    this.logStorageStubber = logStorageStubber;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    logStorage = logStorageStubber.apply(new FsLogStorage(config));

    startContext.run(this::openLogStorage);
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stopContext.run(logStorage::close);
  }

  @Override
  public LogStorage get() {
    return logStorage;
  }

  public void openLogStorage() {
    try {
      logStorage.open();
    } catch (IOException e) {
      // retry until success
      openLogStorage();
    }
  }
}
