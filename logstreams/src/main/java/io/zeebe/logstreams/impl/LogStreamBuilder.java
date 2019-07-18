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
import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.impl.service.FsLogStorageService;
import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.File;
import java.util.Objects;
import java.util.function.Function;
import org.agrona.concurrent.status.AtomicLongPosition;

public class LogStreamBuilder {
  protected final int partitionId;
  protected final AtomicLongPosition commitPosition = new AtomicLongPosition();
  protected final ActorConditions onCommitPositionUpdatedConditions = new ActorConditions();
  protected ServiceContainer serviceContainer;
  protected String logName;
  protected String logRootPath;
  protected String logDirectory;
  protected int initialLogSegmentId = 0;
  protected boolean deleteOnClose;
  protected int maxAppendBlockSize = 1024 * 1024;
  protected int writeBufferSize = 1024 * 1024 * 8;
  protected int logSegmentSize = 1024 * 1024 * 128;
  protected Function<FsLogStorage, FsLogStorage> logStorageStubber = Function.identity();

  public LogStreamBuilder(final int partitionId) {
    this.partitionId = partitionId;
  }

  public LogStreamBuilder serviceContainer(final ServiceContainer serviceContainer) {
    this.serviceContainer = serviceContainer;
    return this;
  }

  public LogStreamBuilder logName(final String logName) {
    this.logName = logName;
    return this;
  }

  public LogStreamBuilder logRootPath(final String logRootPath) {
    this.logRootPath = logRootPath;
    return this;
  }

  public LogStreamBuilder logDirectory(final String logDir) {
    this.logDirectory = logDir;
    return this;
  }

  public LogStreamBuilder writeBufferSize(final int writeBufferSize) {
    this.writeBufferSize = writeBufferSize;
    return this;
  }

  public LogStreamBuilder maxAppendBlockSize(final int maxAppendBlockSize) {
    this.maxAppendBlockSize = maxAppendBlockSize;
    return this;
  }

  public LogStreamBuilder initialLogSegmentId(final int logFragmentId) {
    this.initialLogSegmentId = logFragmentId;
    return this;
  }

  public LogStreamBuilder logSegmentSize(final int logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
    return this;
  }

  public LogStreamBuilder deleteOnClose(final boolean deleteOnClose) {
    this.deleteOnClose = deleteOnClose;
    return this;
  }

  public LogStreamBuilder logStorageStubber(
      final Function<FsLogStorage, FsLogStorage> logStorageStubber) {
    this.logStorageStubber = logStorageStubber;
    return this;
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

  public int getMaxAppendBlockSize() {
    return maxAppendBlockSize;
  }

  public ServiceContainer getServiceContainer() {
    return serviceContainer;
  }

  public AtomicLongPosition getCommitPosition() {
    return commitPosition;
  }

  public int getWriteBufferSize() {
    return writeBufferSize;
  }

  public ActorConditions getOnCommitPositionUpdatedConditions() {
    return onCommitPositionUpdatedConditions;
  }

  public ActorFuture<LogStream> build() {
    Objects.requireNonNull(serviceContainer, "serviceContainer");
    validate();

    final CompositeServiceBuilder installOperation =
        serviceContainer.createComposite(logStreamRootServiceName(logName));

    final ServiceName<LogStream> logStreamServiceName = addServices(installOperation);

    return installOperation.installAndReturn(logStreamServiceName);
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

  private void validate() {
    Objects.requireNonNull(logName, "logName");
    ensureGreaterThanOrEqual("partitionId", partitionId, 0);
  }
}
