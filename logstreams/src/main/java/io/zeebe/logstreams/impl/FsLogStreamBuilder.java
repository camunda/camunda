/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.impl;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamRootServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamServiceName;

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.impl.service.FsLogStorageService;
import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamBuilder;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.File;
import java.util.Objects;
import java.util.function.Function;
import org.agrona.concurrent.status.AtomicLongPosition;

public class FsLogStreamBuilder extends LogStreamBuilder<FsLogStreamBuilder> {
  private final AtomicLongPosition commitPosition;
  private final ActorConditions onCommitPositionUpdatedConditions;

  private String logName;
  private String logRootPath;
  private String logDirectory;
  private int initialLogSegmentId = 0;
  private boolean deleteOnClose;
  private int logSegmentSize = 1024 * 1024 * 128;
  private Function<FsLogStorage, FsLogStorage> logStorageStubber;

  public FsLogStreamBuilder(final int partitionId) {
    this.partitionId = partitionId;
    logStorageStubber = Function.identity();
    onCommitPositionUpdatedConditions = new ActorConditions();
    commitPosition = new AtomicLongPosition();
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
    Objects.requireNonNull(serviceContainer, "serviceContainer");
    validate();

    final CompositeServiceBuilder installOperation =
        serviceContainer.createComposite(logStreamRootServiceName(logName));

    final ServiceName<LogStream> logStreamServiceName = addServices(installOperation);

    return installOperation.installAndReturn(logStreamServiceName);
  }

  @Override
  protected void validate() {
    super.validate();

    if (logSegmentSize < maxFragmentSize) {
      throw new IllegalArgumentException(
          String.format(
              "Expected the log segment size greater than the max fragment size of %s, but was %s.",
              ByteValue.ofBytes(maxFragmentSize), ByteValue.ofBytes(logSegmentSize)));
    }
  }

  public FsLogStreamBuilder withLogName(final String logName) {
    this.logName = logName;
    return this;
  }

  public FsLogStreamBuilder withLogRootPath(final String logRootPath) {
    this.logRootPath = logRootPath;
    return this;
  }

  public FsLogStreamBuilder withLogDirectory(final String logDir) {
    logDirectory = logDir;
    return this;
  }

  public FsLogStreamBuilder withLogSegmentSize(final int logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
    return this;
  }

  public FsLogStreamBuilder deleteOnClose(final boolean deleteOnClose) {
    this.deleteOnClose = deleteOnClose;
    return this;
  }

  public FsLogStreamBuilder logStorageStubber(
      final Function<FsLogStorage, FsLogStorage> logStorageStubber) {
    this.logStorageStubber = logStorageStubber;
    return this;
  }

  public int getMaxFragmentSize() {
    return maxFragmentSize;
  }

  public String getLogName() {
    return logName;
  }

  public String getLogDirectory() {
    if (logDirectory == null) {
      logDirectory = logRootPath + File.separatorChar + logName + File.separatorChar;
    }
    return logDirectory;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }

  public AtomicLongPosition getCommitPosition() {
    return commitPosition;
  }

  public ActorConditions getOnCommitPositionUpdatedConditions() {
    return onCommitPositionUpdatedConditions;
  }

  private ServiceName<LogStream> addServices(final CompositeServiceBuilder installOperation) {
    final ServiceName<LogStorage> logStorageServiceName = logStorageServiceName(logName);
    final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

    final FsLogStorageConfiguration storageConfig =
        new FsLogStorageConfiguration(
            logSegmentSize, getLogDirectory(), initialLogSegmentId, deleteOnClose);

    final FsLogStorageService logStorageService =
        new FsLogStorageService(storageConfig, partitionId, logStorageStubber);
    installOperation.createService(logStorageServiceName, logStorageService).install();

    final LogStreamService logStreamService = new LogStreamService(this);
    installOperation
        .createService(logStreamServiceName, logStreamService)
        .dependency(logStorageServiceName, logStreamService.getLogStorageInjector())
        .install();

    return logStreamServiceName;
  }
}
