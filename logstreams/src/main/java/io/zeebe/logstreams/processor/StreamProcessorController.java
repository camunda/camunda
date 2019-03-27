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
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import io.zeebe.util.LangUtil;
import io.zeebe.util.metrics.MetricsManager;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorPriority;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.ActorTask.ActorLifecyclePhase;
import io.zeebe.util.sched.SchedulingHints;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public class StreamProcessorController extends Actor {

  private static final Logger LOG = Loggers.LOGSTREAMS_LOGGER;

  private static final String ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED =
      "Expected to find event with the snapshot position in log stream, but nothing was found. Failed to recover with processor '%s'.";

  private final StreamProcessorFactory streamProcessorFactory;
  private StreamProcessor streamProcessor;
  private final StreamProcessorContext streamProcessorContext;
  private final SnapshotController snapshotController;
  private String partitionId;

  private final LogStreamReader logStreamReader;
  private final LogStreamRecordWriter logStreamWriter;

  private final Duration snapshotPeriod;

  private final ActorScheduler actorScheduler;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private Phase phase = Phase.REPROCESSING;

  private long snapshotPosition = -1L;

  private ActorCondition onCommitPositionUpdatedCondition;

  private boolean suspended = false;

  private StreamProcessorMetrics metrics;
  private DbContext dbContext;
  private ProcessingStateMachine processingStateMachine;

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
    this.snapshotPeriod = context.getSnapshotPeriod();
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

    try {
      LOG.info("Recovering state of partition {} from snapshot", partitionId);
      snapshotPosition = recoverFromSnapshot(logStream.getCommitPosition(), logStream.getTerm());
      final ZeebeDb zeebeDb = snapshotController.openDb();
      LOG.info(
          "Recovered state of partition {} from snapshot at position {}",
          partitionId,
          snapshotPosition);

      dbContext = zeebeDb.createContext();
      streamProcessor = streamProcessorFactory.createProcessor(zeebeDb, dbContext);
      streamProcessor.onOpen(streamProcessorContext);
    } catch (final Exception e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }

    processingStateMachine =
        ProcessingStateMachine.builder()
            .setStreamProcessorContext(streamProcessorContext)
            .setMetrics(metrics)
            .setStreamProcessor(streamProcessor)
            .setDbContext(dbContext)
            .setShouldProcessNext(() -> isOpened() && !isSuspended())
            .setAbortCondition(this::isClosed)
            .build();
  }

  @Override
  protected void onActorStarted() {
    try {
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

  private long recoverFromSnapshot(final long commitPosition, final int term) throws Exception {
    final StateSnapshotMetadata recovered =
        snapshotController.recover(commitPosition, term, this::validateSnapshot);
    final long snapshotPosition = recovered.getLastSuccessfulProcessedEventPosition();

    logStreamReader.seekToFirstEvent(); // reset seek position
    if (!recovered.isInitial()) {
      final boolean found = logStreamReader.seek(snapshotPosition);
      if (found && logStreamReader.hasNext()) {
        logStreamReader.seek(snapshotPosition + 1);
      } else {
        throw new IllegalStateException(
            String.format(ERROR_MESSAGE_RECOVER_FROM_SNAPSHOT_FAILED, getName()));
      }

      snapshotController.purgeAllExcept(recovered);
    }

    return snapshotPosition;
  }

  private boolean validateSnapshot(final StateSnapshotMetadata metadata) {
    final boolean wasFound = logStreamReader.seek(metadata.getLastWrittenEventPosition());
    boolean isValid = false;

    if (wasFound && logStreamReader.hasNext()) {
      final LoggedEvent event = logStreamReader.next();
      isValid = event.getRaftTerm() == metadata.getLastWrittenEventTerm();
    }

    return isValid;
  }

  private void onRecovered() {
    phase = Phase.PROCESSING;

    onCommitPositionUpdatedCondition =
        actor.onCondition(
            getName() + "-on-commit-position-updated", processingStateMachine::readNextEvent);
    streamProcessorContext.logStream.registerOnCommitPositionUpdatedCondition(
        onCommitPositionUpdatedCondition);

    actor.runAtFixedRate(snapshotPeriod, this::createSnapshot);

    // start reading
    streamProcessor.onRecovered();
    actor.submit(processingStateMachine::readNextEvent);
  }

  private void createSnapshot() {
    if (actor.getLifecyclePhase() == ActorLifecyclePhase.STARTED) {
      // run as io-bound actor while writing snapshot
      actor.setSchedulingHints(SchedulingHints.ioBound());
      actor.submit(this::doCreateSnapshot);
    } else {
      doCreateSnapshot();
    }
  }

  private void doCreateSnapshot() {

    final long lastWrittenEventPosition = processingStateMachine.getLastWrittenEventPosition();
    final long lastSuccessfulProcessedEventPosition =
        processingStateMachine.getLastSuccessfulProcessedEventPosition();
    final long lastWrittenPosition =
        lastWrittenEventPosition > lastSuccessfulProcessedEventPosition
            ? lastWrittenEventPosition
            : lastSuccessfulProcessedEventPosition;

    final StateSnapshotMetadata metadata =
        new StateSnapshotMetadata(
            lastSuccessfulProcessedEventPosition,
            lastWrittenPosition,
            streamProcessorContext.getLogStream().getTerm(),
            false);

    writeSnapshot(metadata);

    // reset to cpu bound
    actor.setSchedulingHints(SchedulingHints.cpuBound(ActorPriority.REGULAR));
  }

  private void writeSnapshot(final StateSnapshotMetadata metadata) {
    final long start = System.currentTimeMillis();
    final String name = streamProcessorContext.getName();
    LOG.info(
        "Write snapshot for stream processor {} at event position {}.",
        name,
        metadata.getLastSuccessfulProcessedEventPosition());

    try {
      snapshotController.takeSnapshot(metadata);

      final long snapshotCreationTime = System.currentTimeMillis() - start;
      LOG.info("Creation of snapshot {} took {} ms.", name, snapshotCreationTime);
      metrics.recordSnapshotCreationTime(snapshotCreationTime);

      snapshotPosition = processingStateMachine.getLastSuccessfulProcessedEventPosition();
    } catch (final Exception e) {
      LOG.error("Stream processor '{}' failed. Can not write snapshot.", getName(), e);
    }
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
            createSnapshot();
            try {
              snapshotController.close();
            } catch (Exception e) {
              LOG.error("Error on closing snapshotController.", e);
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
