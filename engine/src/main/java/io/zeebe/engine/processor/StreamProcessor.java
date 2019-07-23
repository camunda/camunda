/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor;

import static io.zeebe.engine.processor.TypedEventRegistry.EVENT_REGISTRY;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.LangUtil;
import io.zeebe.util.ReflectUtil;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public class StreamProcessor extends Actor implements Service<StreamProcessor> {
  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover '%s'.";
  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private final ActorScheduler actorScheduler;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private final List<StreamProcessorLifecycleAware> lifecycleAwareListeners;

  // log stream
  private final LogStream logStream;
  private final int partitionId;
  // snapshotting
  private final ZeebeDb zeebeDb;
  // processing
  private final ProcessingContext processingContext;
  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private final LogStreamReader logStreamReader;
  private ActorCondition onCommitPositionUpdatedCondition;
  private long snapshotPosition = -1L;
  private ProcessingStateMachine processingStateMachine;

  private Phase phase = Phase.REPROCESSING;
  private CompletableActorFuture<Void> openFuture;
  private CompletableActorFuture<Void> closeFuture = CompletableActorFuture.completed(null);

  protected StreamProcessor(final StreamProcessorBuilder context) {
    this.actorScheduler = context.getActorScheduler();
    this.lifecycleAwareListeners = context.getLifecycleListeners();

    this.typedRecordProcessorFactory = context.getTypedRecordProcessorFactory();
    this.zeebeDb = context.getZeebeDb();

    final EnumMap<ValueType, UnifiedRecordValue> eventCache = new EnumMap<>(ValueType.class);
    EVENT_REGISTRY.forEach((t, c) -> eventCache.put(t, ReflectUtil.newInstance(c)));

    processingContext =
        context
            .getProcessingContext()
            .eventCache(Collections.unmodifiableMap(eventCache))
            .actor(actor)
            .abortCondition(this::isClosed);
    this.logStreamReader = processingContext.getLogStreamReader();
    this.logStream = processingContext.getLogStream();
    this.partitionId = logStream.getPartitionId();
  }

  public static StreamProcessorBuilder builder() {
    return new StreamProcessorBuilder();
  }

  @Override
  public String getName() {
    return "stream-processor";
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.debug("Recovering state of partition {} from snapshot", partitionId);
      snapshotPosition = recoverFromSnapshot();

      initProcessors();

      lifecycleAwareListeners.forEach(l -> l.onOpen(processingContext));
    } catch (final Throwable e) {
      onFailure(e);
      LangUtil.rethrowUnchecked(e);
    }

    try {
      processingStateMachine = new ProcessingStateMachine(processingContext, this::isOpened);
      openFuture.complete(null);

      final ReProcessingStateMachine reProcessingStateMachine =
          new ReProcessingStateMachine(processingContext);

      final ActorFuture<Void> recoverFuture =
          reProcessingStateMachine.startRecover(snapshotPosition);

      actor.runOnCompletion(
          recoverFuture,
          (v, throwable) -> {
            if (throwable != null) {
              LOG.error("Unexpected error on recovery happens.", throwable);
              onFailure(throwable);
            } else {
              onRecovered();
            }
          });
    } catch (final RuntimeException e) {
      onFailure(e);
      throw e;
    }
  }

  @Override
  protected void onActorClosing() {
    processingContext.getLogStreamReader().close();

    if (onCommitPositionUpdatedCondition != null) {
      logStream.removeOnCommitPositionUpdatedCondition(onCommitPositionUpdatedCondition);
      onCommitPositionUpdatedCondition = null;
    }
  }

  @Override
  protected void onActorClosed() {
    closeFuture.complete(null);
    LOG.debug("Closed stream processor controller {}.", getName());
  }

  @Override
  protected void onActorCloseRequested() {
    if (!isFailed()) {
      lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onClose);
    }
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.async(openAsync(), true);
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(closeAsync());
  }

  @Override
  public StreamProcessor get() {
    return this;
  }

  public ActorFuture<Void> openAsync() {
    if (isOpened.compareAndSet(false, true)) {
      openFuture = new CompletableActorFuture<>();
      actorScheduler.submitActor(this);
    }
    return openFuture;
  }

  private void initProcessors() {
    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(processingContext);

    lifecycleAwareListeners.addAll(typedRecordProcessors.getLifecycleListeners());
    final RecordProcessorMap recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();
    recordProcessorMap.values().forEachRemaining(this.lifecycleAwareListeners::add);

    processingContext.recordProcessorMap(recordProcessorMap);
  }

  private long recoverFromSnapshot() {
    final ZeebeState zeebeState = recoverState();
    final long snapshotPosition = zeebeState.getLastSuccessfulProcessedRecordPosition();

    final boolean failedToRecoverReader = !logStreamReader.seekToNextEvent(snapshotPosition);
    if (failedToRecoverReader) {
      throw new IllegalStateException(
          String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
    }

    LOG.info(
        "Recovered state of partition {} from snapshot at position {}",
        partitionId,
        snapshotPosition);
    return snapshotPosition;
  }

  private ZeebeState recoverState() {
    final DbContext dbContext = zeebeDb.createContext();
    final ZeebeState zeebeState = new ZeebeState(partitionId, zeebeDb, dbContext);

    processingContext.dbContext(dbContext);
    processingContext.zeebeState(zeebeState);

    return zeebeState;
  }

  private void onRecovered() {
    phase = Phase.PROCESSING;
    onCommitPositionUpdatedCondition =
        actor.onCondition(
            getName() + "-on-commit-position-updated", processingStateMachine::readNextEvent);
    logStream.registerOnCommitPositionUpdatedCondition(onCommitPositionUpdatedCondition);

    // start reading
    lifecycleAwareListeners.forEach(l -> l.onRecovered(processingContext));
    actor.submit(processingStateMachine::readNextEvent);
  }

  public ActorFuture<Void> closeAsync() {
    if (isOpened.compareAndSet(true, false)) {
      closeFuture = new CompletableActorFuture<>();
      actor.close();
    }
    return closeFuture;
  }

  private void onFailure(Throwable throwable) {
    phase = Phase.FAILED;
    openFuture.completeExceptionally(throwable);
    closeFuture = new CompletableActorFuture<>();
    isOpened.set(false);
    actor.close();
  }

  public boolean isOpened() {
    return isOpened.get();
  }

  public boolean isClosed() {
    return !isOpened.get();
  }

  public boolean isFailed() {
    return phase == Phase.FAILED;
  }

  public ActorFuture<Long> getLastProcessedPositionAsync() {
    return actor.call(processingStateMachine::getLastSuccessfulProcessedEventPosition);
  }

  public ActorFuture<Long> getLastWrittenPositionAsync() {
    return actor.call(processingStateMachine::getLastWrittenEventPosition);
  }

  private enum Phase {
    REPROCESSING,
    PROCESSING,
    FAILED
  }
}
