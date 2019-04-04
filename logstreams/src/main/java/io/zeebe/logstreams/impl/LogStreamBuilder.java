/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.impl;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logBlockIndexServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logBlockIndexWriterService;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamRootServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamServiceName;
import static io.zeebe.util.EnsureUtil.ensureFalse;
import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;

import io.zeebe.logstreams.impl.log.fs.FsLogStorage;
import io.zeebe.logstreams.impl.log.fs.FsLogStorageConfiguration;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.impl.service.FsLogStorageService;
import io.zeebe.logstreams.impl.service.LogBlockIndexService;
import io.zeebe.logstreams.impl.service.LogBlockIndexWriterService;
import io.zeebe.logstreams.impl.service.LogStreamService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import java.io.File;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import org.agrona.concurrent.status.AtomicLongPosition;

public class LogStreamBuilder {
  protected final int partitionId;

  protected ServiceContainer serviceContainer;

  protected String logName;
  protected String logRootPath;
  protected String logDirectory;

  protected int initialLogSegmentId = 0;
  protected boolean deleteOnClose;

  protected int maxAppendBlockSize = 1024 * 1024;
  protected int writeBufferSize = 1024 * 1024 * 8;
  protected int logSegmentSize = 1024 * 1024 * 128;
  protected int indexBlockSize = 1024 * 1024 * 4;
  protected float deviation = LogBlockIndexWriter.DEFAULT_DEVIATION;
  protected int readBlockSize = 1024;
  protected int maxSnapshots = 1;

  protected Duration snapshotPeriod = Duration.ofMinutes(1);

  protected final AtomicLongPosition commitPosition = new AtomicLongPosition();
  protected final ActorConditions onCommitPositionUpdatedConditions = new ActorConditions();

  protected Function<FsLogStorage, FsLogStorage> logStorageStubber = Function.identity();
  private StateStorage stateStorage;

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

  public LogStreamBuilder indexBlockSize(final int indexBlockSize) {
    this.indexBlockSize = indexBlockSize;
    return this;
  }

  public LogStreamBuilder deviation(final float deviation) {
    this.deviation = deviation;
    return this;
  }

  public LogStreamBuilder indexStateStorage(StateStorage stateStorage) {
    this.stateStorage = stateStorage;
    return this;
  }

  public LogStreamBuilder snapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
    return this;
  }

  public LogStreamBuilder maxSnapshots(final int maxSnapshots) {
    this.maxSnapshots = maxSnapshots;
    return this;
  }

  public LogStreamBuilder readBlockSize(final int readBlockSize) {
    this.readBlockSize = readBlockSize;
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

  public int getIndexBlockSize() {
    return indexBlockSize;
  }

  public int getReadBlockSize() {
    return readBlockSize;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public StateStorage getStateStorage() {
    return stateStorage;
  }

  public int getMaxSnapshots() {
    return maxSnapshots;
  }

  public float getDeviation() {
    return deviation;
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

  public ServiceName<LogStream> buildWith(final CompositeServiceBuilder composite) {
    validate();

    return addServices(composite);
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
    final ServiceName<LogBlockIndex> logBlockIndexServiceName = logBlockIndexServiceName(logName);
    final ServiceName<LogBlockIndexWriter> logBlockIndexWriterServiceName =
        logBlockIndexWriterService(logName);
    final ServiceName<LogStream> logStreamServiceName = logStreamServiceName(logName);

    final FsLogStorageConfiguration storageConfig =
        new FsLogStorageConfiguration(
            logSegmentSize, getLogDirectory(), initialLogSegmentId, deleteOnClose);

    final FsLogStorageService logStorageService =
        new FsLogStorageService(storageConfig, partitionId, logStorageStubber);
    installOperation.createService(logStorageServiceName, logStorageService).install();

    final LogBlockIndexService logBlockIndexService = new LogBlockIndexService(stateStorage);
    installOperation.createService(logBlockIndexServiceName, logBlockIndexService).install();

    final LogBlockIndexWriterService logBlockIndexWriterService =
        new LogBlockIndexWriterService(this);
    installOperation
        .createService(logBlockIndexWriterServiceName, logBlockIndexWriterService)
        .dependency(logStorageServiceName, logBlockIndexWriterService.getLogStorageInjector())
        .dependency(logBlockIndexServiceName, logBlockIndexWriterService.getLogBlockIndexInjector())
        .install();

    final LogStreamService logStreamService = new LogStreamService(this);
    installOperation
        .createService(logStreamServiceName, logStreamService)
        .dependency(logStorageServiceName, logStreamService.getLogStorageInjector())
        .dependency(logBlockIndexServiceName, logStreamService.getLogBlockIndexInjector())
        .dependency(
            logBlockIndexWriterServiceName, logStreamService.getLogBockIndexWriterInjector())
        .install();

    return logStreamServiceName;
  }

  private void validate() {
    Objects.requireNonNull(logName, "logName");
    ensureGreaterThanOrEqual("partitionId", partitionId, 0);
    ensureFalse("deviation", deviation <= 0f || deviation > 1f);
    Objects.requireNonNull(stateStorage, "indexStateStorage");
  }
}
