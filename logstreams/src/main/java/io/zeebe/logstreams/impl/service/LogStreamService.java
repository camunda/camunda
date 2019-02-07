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
package io.zeebe.logstreams.impl.service;

import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.distributedLogPartitionServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageAppenderRootService;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageAppenderServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStorageCommitListenerServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logStreamRootServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logWriteBufferServiceName;
import static io.zeebe.logstreams.impl.service.LogStreamServiceNames.logWriteBufferSubscriptionServiceName;
import static io.zeebe.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static io.zeebe.logstreams.log.LogStreamUtil.getAddressForPosition;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.DispatcherBuilder;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.Subscription;
import io.zeebe.logstreams.impl.LogBlockIndexWriter;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.LogStorageCommitListener;
import io.zeebe.logstreams.impl.LogStorageCommitListenerService;
import io.zeebe.logstreams.impl.LogStreamBuilder;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.servicecontainer.CompositeServiceBuilder;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.ByteValue;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.channel.ActorConditions;
import io.zeebe.util.sched.future.ActorFuture;
import org.agrona.concurrent.status.Position;

public class LogStreamService implements LogStream, Service<LogStream> {
  private static final String APPENDER_SUBSCRIPTION_NAME = "appender";

  private final Injector<LogStorage> logStorageInjector = new Injector<>();
  private final Injector<LogBlockIndex> logBlockIndexInjector = new Injector<>();
  private final Injector<LogBlockIndexWriter> logBockIndexWriterInjector = new Injector<>();

  private final ServiceContainer serviceContainer;

  private final ActorConditions onLogStorageAppendedConditions = new ActorConditions();
  private final ActorConditions onCommitPositionUpdatedConditions;

  private final String logName;
  private final int partitionId;

  private final ByteValue writeBufferSize;
  private final int maxAppendBlockSize;

  private final Position commitPosition;
  private volatile int term = 0;

  private ServiceStartContext serviceContext;

  private LogStorage logStorage;
  private LogBlockIndex logBlockIndex;
  private LogBlockIndexWriter logBlockIndexWriter;

  private ActorFuture<Dispatcher> writeBufferFuture;
  private ActorFuture<LogStorageAppender> appenderFuture;
  private Dispatcher writeBuffer;
  private LogStorageAppender appender;
  private ActorFuture<LogStorageCommitListener> committerFuture;
  private LogStorageCommitListener committer;

  public LogStreamService(final LogStreamBuilder builder) {
    this.logName = builder.getLogName();
    this.partitionId = builder.getPartitionId();
    this.serviceContainer = builder.getServiceContainer();
    this.onCommitPositionUpdatedConditions = builder.getOnCommitPositionUpdatedConditions();
    this.commitPosition = builder.getCommitPosition();
    this.writeBufferSize = ByteValue.ofBytes(builder.getWriteBufferSize());
    this.maxAppendBlockSize = builder.getMaxAppendBlockSize();
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    commitPosition.setVolatile(INVALID_ADDRESS);

    serviceContext = startContext;
    logStorage = logStorageInjector.getValue();
    logBlockIndex = logBlockIndexInjector.getValue();
    logBlockIndexWriter = logBockIndexWriterInjector.getValue();

    committerFuture = openCommitListener();
  }

  private ActorFuture<LogStorageCommitListener> openCommitListener() {
    final LogStorageCommitListenerService logStorageAppenderListenerService =
        new LogStorageCommitListenerService(this, onLogStorageAppendedConditions);

    // Service that listens to commit events from distributed-log and writes to logStorage
    return serviceContext
        .createService(
            logStorageCommitListenerServiceName(logName), logStorageAppenderListenerService)
        .dependency(
            distributedLogPartitionServiceName(logName),
            logStorageAppenderListenerService.getDistributedLogstreamInjector())
        .install();
  }

  private ActorFuture<Void> closeCommitListener() {
    return serviceContext.removeService(logStorageCommitListenerServiceName(logName));
  }

  @Override
  public ActorFuture<LogStorageAppender> openAppender() {
    final ServiceName<Void> logStorageAppenderRootService = logStorageAppenderRootService(logName);
    final ServiceName<Dispatcher> logWriteBufferServiceName = logWriteBufferServiceName(logName);
    final ServiceName<Subscription> appenderSubscriptionServiceName =
        logWriteBufferSubscriptionServiceName(logName, APPENDER_SUBSCRIPTION_NAME);
    final ServiceName<LogStorageAppender> logStorageAppenderServiceName =
        logStorageAppenderServiceName(logName);

    final DispatcherBuilder writeBufferBuilder =
        Dispatchers.create(logWriteBufferServiceName.getName()).bufferSize(writeBufferSize);

    final CompositeServiceBuilder installOperation =
        serviceContext.createComposite(logStorageAppenderRootService);

    final LogWriteBufferService writeBufferService = new LogWriteBufferService(writeBufferBuilder);
    writeBufferFuture =
        installOperation
            .createService(logWriteBufferServiceName, writeBufferService)
            .dependency(
                logStorageInjector.getInjectedServiceName(),
                writeBufferService.getLogStorageInjector())
            .dependency(
                logBlockIndexInjector.getInjectedServiceName(),
                writeBufferService.getLogBlockIndexInjector())
            .install();

    final LogWriteBufferSubscriptionService subscriptionService =
        new LogWriteBufferSubscriptionService(APPENDER_SUBSCRIPTION_NAME);
    installOperation
        .createService(appenderSubscriptionServiceName, subscriptionService)
        .dependency(logWriteBufferServiceName, subscriptionService.getWritebufferInjector())
        .install();

    final LogStorageAppenderService appenderService =
        new LogStorageAppenderService(onLogStorageAppendedConditions, maxAppendBlockSize);
    appenderFuture =
        installOperation
            .createService(logStorageAppenderServiceName, appenderService)
            .dependency(
                appenderSubscriptionServiceName, appenderService.getAppenderSubscriptionInjector())
            .dependency(
                logStorageInjector.getInjectedServiceName(),
                appenderService.getLogStorageInjector())
            .dependency(
                distributedLogPartitionServiceName(logName),
                appenderService.getDistributedLogstreamInjector())
            .install();

    return installOperation.installAndReturn(logStorageAppenderServiceName);
  }

  @Override
  public ActorFuture<Void> closeAppender() {
    appenderFuture = null;
    writeBufferFuture = null;
    appender = null;
    writeBuffer = null;

    return serviceContext.removeService(logStorageAppenderRootService(logName));
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    // nothing to do
  }

  @Override
  public LogStream get() {
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public String getLogName() {
    return logName;
  }

  @Override
  public void close() {
    closeAsync().join();
  }

  @Override
  public ActorFuture<Void> closeAsync() {
    closeCommitListener();
    return serviceContainer.removeService(logStreamRootServiceName(logName));
  }

  @Override
  public LogStorage getLogStorage() {
    return logStorage;
  }

  @Override
  public LogBlockIndex getLogBlockIndex() {
    return logBlockIndex;
  }

  @Override
  public LogBlockIndexWriter getLogBlockIndexWriter() {
    return logBlockIndexWriter;
  }

  @Override
  public Dispatcher getWriteBuffer() {
    if (writeBuffer == null && writeBufferFuture != null) {
      writeBuffer = writeBufferFuture.join();
    }
    return writeBuffer;
  }

  @Override
  public LogStorageAppender getLogStorageAppender() {
    if (appender == null && appenderFuture != null) {
      appender = appenderFuture.join();
    }
    return appender;
  }

  @Override
  public LogStorageCommitListener getLogStorageCommitter() {
    if (committer == null && committerFuture != null) {
      committer = committerFuture.join();
    }
    return committer;
  }

  @Override
  public long getCommitPosition() {
    return commitPosition.get();
  }

  @Override
  public void truncate(final long position) {
    if (position <= getCommitPosition()) {
      throw new IllegalArgumentException("Can't truncate position which is already committed");
    }

    final long truncateAddress = getAddressForPosition(this, position);
    if (truncateAddress != INVALID_ADDRESS) {
      logStorage.truncate(truncateAddress);
    } else {
      throw new IllegalArgumentException(
          String.format("Truncation failed! Position %d was not found.", position));
    }
  }

  @Override
  public void setCommitPosition(final long commitPosition) {
    this.commitPosition.setOrdered(commitPosition);

    onCommitPositionUpdatedConditions.signalConsumers();
  }

  @Override
  public void registerOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    onCommitPositionUpdatedConditions.registerConsumer(condition);
  }

  @Override
  public void removeOnCommitPositionUpdatedCondition(final ActorCondition condition) {
    onCommitPositionUpdatedConditions.removeConsumer(condition);
  }

  @Override
  public void registerOnAppendCondition(final ActorCondition condition) {
    onLogStorageAppendedConditions.registerConsumer(condition);
  }

  @Override
  public void removeOnAppendCondition(final ActorCondition condition) {
    onLogStorageAppendedConditions.removeConsumer(condition);
  }

  @Override
  public int getTerm() {
    return term;
  }

  @Override
  public void setTerm(final int term) {
    this.term = term;
  }

  public Injector<LogBlockIndex> getLogBlockIndexInjector() {
    return logBlockIndexInjector;
  }

  public Injector<LogBlockIndexWriter> getLogBockIndexWriterInjector() {
    return logBockIndexWriterInjector;
  }

  public Injector<LogStorage> getLogStorageInjector() {
    return logStorageInjector;
  }
}
