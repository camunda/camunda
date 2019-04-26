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
package io.zeebe.logstreams.processor;

import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.util.LangUtil;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public class StreamProcessorController extends Actor {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position %s in log stream, but nothing was found. Failed to recover with processor '%s'.";

  private final StreamProcessorFactory streamProcessorFactory;
  private StreamProcessor streamProcessor;
  private final StreamProcessorContext streamProcessorContext;
  private final SnapshotController snapshotController;
  private String partitionId;

  private final LogStreamReader logStreamReader;
  private final LogStreamRecordWriter logStreamWriter;

  private final ActorScheduler actorScheduler;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);

  private Phase phase = Phase.REPROCESSING;

  private long snapshotPosition = -1L;

  private ActorCondition onCommitPositionUpdatedCondition;

  private boolean suspended = false;

  private StreamProcessorMetrics metrics;
  private DbContext dbContext;
  private ProcessingStateMachine processingStateMachine;
  private AsyncSnapshotDirector asyncSnapshotDirector;
  private final int maxSnapshots;

  public StreamProcessorController(final StreamProcessorContext context) {
    this.streamProcessorContext = context;
    this.streamProcessorContext.setActorControl(actor);

    this.streamProcessorContext.setSuspendRunnable(this::suspend);
    this.streamProcessorContext.setResumeRunnable(this::resume);

    this.actorScheduler = context.getActorScheduler();

    this.streamProcessorFactory = context.getStreamProcessorFactory();
    this.snapshotController = context.getSnapshotController();

    this.logStreamReader = context.getLogStreamReader();
    this.logStreamWriter = context.getLogStreamWriter();
    this.maxSnapshots = context.getMaxSnapshots();
  }

  @Override
  public String getName() {
    return streamProcessorContext.getName();
  }

  public ActorFuture<Void> openAsync() {
    if (isOpened.compareAndSet(false, true)) {
      return actorScheduler.submitActor(this, true);
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  protected void onActorStarting() {
    final LogStream logStream = streamProcessorContext.getLogStream();

    final MetricsManager metricsManager = actorScheduler.getMetricsManager();
    partitionId = String.valueOf(logStream.getPartitionId());
    final String processorName = getName();

    metrics = new StreamProcessorMetrics(metricsManager, processorName, partitionId);

    logStreamReader.wrap(logStream);
    logStreamWriter.wrap(logStream);
  }

  @Override
  protected void onActorStarted() {
    try {
      LOG.info("Recovering state of partition {} from snapshot", partitionId);
      snapshotPosition = recoverFromSnapshot();

      streamProcessor.onOpen(streamProcessorContext);
    } catch (final Throwable e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }

    try {
      processingStateMachine =
          ProcessingStateMachine.builder()
              .setStreamProcessorContext(streamProcessorContext)
              .setMetrics(metrics)
              .setStreamProcessor(streamProcessor)
              .setDbContext(dbContext)
              .setShouldProcessNext(() -> isOpened() && !isSuspended())
              .setAbortCondition(this::isClosed)
              .build();

      final ReProcessingStateMachine reProcessingStateMachine =
          ReProcessingStateMachine.builder()
              .setStreamProcessorContext(streamProcessorContext)
              .setStreamProcessor(streamProcessor)
              .setDbContext(dbContext)
              .setAbortCondition(this::isClosed)
              .build();

      final ActorFuture<Void> recoverFuture =
          reProcessingStateMachine.startRecover(snapshotPosition);

      actor.runOnCompletion(
          recoverFuture,
          (v, throwable) -> {
            if (throwable != null) {
              LOG.error("Unexpected error on recovery happens.", throwable);
              onFailure();
            } else {
              onRecovered();
            }
          });
    } catch (final RuntimeException e) {
      onFailure();
      throw e;
    }
  }

  private long recoverFromSnapshot() throws Exception {
    final long lowerBoundSnapshotPosition = snapshotController.recover();
    final ZeebeDb zeebeDb = snapshotController.openDb();

    dbContext = zeebeDb.createContext();
    streamProcessor = streamProcessorFactory.createProcessor(zeebeDb, dbContext);

    final long snapshotPosition = streamProcessor.getPositionToRecoveryFrom();
    logStreamReader.seekToFirstEvent(); // reset seek position
    if (lowerBoundSnapshotPosition > -1 && snapshotPosition > -1) {
      final boolean found = logStreamReader.seek(snapshotPosition);
      if (found && logStreamReader.hasNext()) {
        logStreamReader.seek(snapshotPosition + 1);
      } else {
        throw new IllegalStateException(
            String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, snapshotPosition, getName()));
      }
    }

    LOG.info(
        "Recovered state of partition {} from snapshot at position {}",
        partitionId,
        snapshotPosition);
    return snapshotPosition;
  }

  private void onRecovered() {
    phase = Phase.PROCESSING;

    final LogStream logStream = streamProcessorContext.getLogStream();
    asyncSnapshotDirector =
        new AsyncSnapshotDirector(
            streamProcessorContext.name,
            streamProcessorContext.snapshotPeriod,
            processingStateMachine::getLastProcessedPositionAsync,
            processingStateMachine::getLastWrittenPositionAsync,
            snapshotController,
            logStream::registerOnCommitPositionUpdatedCondition,
            logStream::removeOnCommitPositionUpdatedCondition,
            logStream::getCommitPosition,
            metrics,
            maxSnapshots);

    actorScheduler.submitActor(asyncSnapshotDirector);

    onCommitPositionUpdatedCondition =
        actor.onCondition(
            getName() + "-on-commit-position-updated", processingStateMachine::readNextEvent);
    logStream.registerOnCommitPositionUpdatedCondition(onCommitPositionUpdatedCondition);

    // start reading
    streamProcessor.onRecovered();
    actor.submit(processingStateMachine::readNextEvent);
  }

  public ActorFuture<Void> closeAsync() {
    if (isOpened.compareAndSet(true, false)) {
      return actor.close();
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  @Override
  protected void onActorCloseRequested() {
    if (!isFailed()) {
      streamProcessor.onClose();
    }
  }

  @Override
  protected void onActorClosing() {
    metrics.close();

    if (!isFailed()) {
      actor.run(
          () -> {
            final LogStream logStream = streamProcessorContext.logStream;
            if (asyncSnapshotDirector != null) {
              LOG.info("On closing, will try to enforce snapshot creation.");
              actor.runOnCompletionBlockingCurrentPhase(
                  asyncSnapshotDirector.enforceSnapshotCreation(
                      processingStateMachine.getLastWrittenEventPosition(),
                      logStream.getCommitPosition()),
                  (v, ex) -> {
                    try {
                      asyncSnapshotDirector.close();
                      snapshotController.close();
                    } catch (Exception e) {
                      LOG.error("Error on closing snapshotController.", e);
                    }
                  });
            } else {
              try {
                snapshotController.close();
              } catch (Exception e) {
                LOG.error("Error on closing snapshotController.", e);
              }
            }
          });
    }

    streamProcessorContext.getLogStreamReader().close();

    if (onCommitPositionUpdatedCondition != null) {
      streamProcessorContext.logStream.removeOnCommitPositionUpdatedCondition(
          onCommitPositionUpdatedCondition);
      onCommitPositionUpdatedCondition = null;
    }
  }

  @Override
  protected void onActorClosed() {
    LOG.debug("Closed stream processor controller {}.", getName());
  }

  private void onFailure() {
    phase = Phase.FAILED;

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

  public boolean isSuspended() {
    return suspended;
  }

  private void suspend() {
    suspended = true;
  }

  private void resume() {
    suspended = false;

    // if state is REPROCESSING, we do nothing, because
    // processing will be triggered once reprocessing is finished anyway
    if (phase == Phase.PROCESSING) {
      actor.submit(processingStateMachine::readNextEvent);
    }
  }

  private enum Phase {
    REPROCESSING,
    PROCESSING,
    FAILED
  }
}
