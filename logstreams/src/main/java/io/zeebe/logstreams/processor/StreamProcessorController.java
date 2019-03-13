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

import io.zeebe.db.ZeebeDb;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.spi.SnapshotController;
import io.zeebe.logstreams.state.StateSnapshotMetadata;
import io.zeebe.util.LangUtil;
import io.zeebe.util.exception.RecoverableException;
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
  private static final String ERROR_MESSAGE_REPROCESSING_NO_SOURCE_EVENT =
      "Expected to find last source event position '%d', but last position was '%d'. Failed to reprocess on processor '%s'";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT =
      "Expected to find last source event position '%d', but found no next event. Failed to reprocess on processor '%s'";
  private static final String ERROR_MESSAGE_REPROCESSING_FAILED_SKIP_EVENT =
      "Expected to successfully reprocess event '{}' on processor '{}', but caught an exception.";
  private static final String ERROR_MESSAGE_REPROCESSING_FAILED_RETRY_REPROCESSING =
      "Expected to reprocess event '{}' successfully on stream processor '{}', but caught recoverable exception. Retry reprocessing.";

  private static final String ERROR_MESSAGE_WRITE_FAILED =
      "Expected to successfully write event with processor '{}', but caught an exception. Stop processing further events.";
  private static final String ERROR_MESSAGE_EXECUTE_SITE_EFFECTS_FAILED =
      "Expected to successfully execute side effects with processor '{}', but caught an exception. Stop processing further events.";
  private static final String ERROR_MESSAGE_UPDATE_STATE_FAILED =
      "Expected to successfully update state with processor '{}', but caught an exception. Stop processing further events.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT =
      "Expected to successfully process event '{}' with processor '{}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING =
      "Expected to process event '{}' successfully on stream processor '{}', but caught recoverable exception. Retry processing.";

  public static final Duration PROCESSING_RETRY_DELAY = Duration.ofMillis(250);

  private final StreamProcessorFactory streamProcessorFactory;
  private StreamProcessor streamProcessor;
  private final StreamProcessorContext streamProcessorContext;
  private final SnapshotController snapshotController;

  private final LogStreamReader logStreamReader;
  private final LogStreamRecordWriter logStreamWriter;

  private final Duration snapshotPeriod;

  private final ActorScheduler actorScheduler;
  private final AtomicBoolean isOpened = new AtomicBoolean(false);
  private Phase phase = Phase.REPROCESSING;

  private final EventFilter eventFilter;
  private final boolean isReadOnlyProcessor;

  private final Runnable readNextEvent = this::readNextEvent;

  private long snapshotPosition = -1L;
  private long lastSourceEventPosition = -1L;
  private long eventPosition = -1L;
  private long lastSuccessfulProcessedEventPosition = -1L;
  private long lastWrittenEventPosition = -1L;

  private LoggedEvent currentEvent;
  private EventProcessor eventProcessor;
  private ActorCondition onCommitPositionUpdatedCondition;

  private boolean suspended = false;

  private StreamProcessorMetrics metrics;
  private ZeebeDb zeebeDb;

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
    this.eventFilter = context.getEventFilter();
    this.isReadOnlyProcessor = context.isReadOnlyProcessor();
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
    final String partitionId = String.valueOf(logStream.getPartitionId());
    final String processorName = getName();

    metrics = new StreamProcessorMetrics(metricsManager, processorName, partitionId);

    logStreamReader.wrap(logStream);
    logStreamWriter.wrap(logStream);

    try {

      snapshotPosition = recoverFromSnapshot(logStream.getCommitPosition(), logStream.getTerm());
      lastSourceEventPosition = seekFromSnapshotPositionToLastSourceEvent();

      zeebeDb = snapshotController.openDb();
      streamProcessor = streamProcessorFactory.createProcessor(zeebeDb);
      streamProcessor.onOpen(streamProcessorContext);
    } catch (final Exception e) {
      onFailure();
      LangUtil.rethrowUnchecked(e);
    }
  }

  @Override
  protected void onActorStarted() {
    try {
      if (lastSourceEventPosition > snapshotPosition) {
        reprocessNextEvent();
      } else {
        onRecovered();
      }
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

  private long seekFromSnapshotPositionToLastSourceEvent() {
    long lastSourceEventPosition = -1L;

    if (!isReadOnlyProcessor && logStreamReader.hasNext()) {
      lastSourceEventPosition = snapshotPosition;
      while (logStreamReader.hasNext()) {
        final LoggedEvent newEvent = logStreamReader.next();

        // ignore events from other producers
        if (newEvent.getProducerId() == streamProcessorContext.getId()) {
          final long sourceEventPosition = newEvent.getSourceEventPosition();
          if (sourceEventPosition > 0 && sourceEventPosition > lastSourceEventPosition) {
            lastSourceEventPosition = sourceEventPosition;
          }
        }
      }

      // reset position
      logStreamReader.seek(snapshotPosition + 1);
    }

    return lastSourceEventPosition;
  }

  private void reprocessNextEvent() {
    try {
      if (logStreamReader.hasNext()) {
        currentEvent = logStreamReader.next();
        if (currentEvent.getPosition() > lastSourceEventPosition) {
          throw new IllegalStateException(
              String.format(
                  ERROR_MESSAGE_REPROCESSING_NO_SOURCE_EVENT,
                  lastSourceEventPosition,
                  currentEvent.getPosition(),
                  getName()));
        }

        reprocessEvent(currentEvent);
      } else {
        throw new IllegalStateException(
            String.format(
                ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT, lastSourceEventPosition, getName()));
      }
    } catch (final RuntimeException e) {
      onFailure();
      throw e;
    }
  }

  private void reprocessEvent(final LoggedEvent currentEvent) {
    if (eventFilter == null || eventFilter.applies(currentEvent)) {
      try {
        final EventProcessor eventProcessor = streamProcessor.onEvent(currentEvent);
        if (eventProcessor != null) {
          try {
            // don't execute side effects or write events
            zeebeDb.transaction(eventProcessor::processEvent);
          } catch (final RecoverableException recoverableException) {
            // recoverable
            LOG.error(
                ERROR_MESSAGE_REPROCESSING_FAILED_RETRY_REPROCESSING,
                currentEvent,
                getName(),
                recoverableException);
            actor.runDelayed(PROCESSING_RETRY_DELAY, () -> processEvent(currentEvent));
            return;
          } catch (final Exception e) {
            LOG.error(ERROR_MESSAGE_REPROCESSING_FAILED_SKIP_EVENT, currentEvent, getName(), e);
            zeebeDb.transaction(() -> eventProcessor.processingFailed(e));
          }
        }
      } catch (final Exception e) {
        LOG.error(ERROR_MESSAGE_REPROCESSING_FAILED_SKIP_EVENT, currentEvent, getName(), e);
      }
    }

    onRecordReprocessed(currentEvent);
  }

  private void onRecordReprocessed(final LoggedEvent currentEvent) {
    if (currentEvent.getPosition() == lastSourceEventPosition) {
      onRecovered();
    } else {
      actor.submit(this::reprocessNextEvent);
    }
  }

  private void onRecovered() {
    phase = Phase.PROCESSING;

    onCommitPositionUpdatedCondition =
        actor.onCondition(getName() + "-on-commit-position-updated", readNextEvent);
    streamProcessorContext.logStream.registerOnCommitPositionUpdatedCondition(
        onCommitPositionUpdatedCondition);

    actor.runAtFixedRate(snapshotPeriod, this::createSnapshot);

    // start reading
    streamProcessor.onRecovered();
    actor.submit(readNextEvent);
  }

  private void readNextEvent() {
    if (isOpened() && !isSuspended() && logStreamReader.hasNext() && eventProcessor == null) {
      currentEvent = logStreamReader.next();

      if (eventFilter == null || eventFilter.applies(currentEvent)) {
        processEvent(currentEvent);
      } else {
        skipRecord();
      }
    }
  }

  private void processEvent(final LoggedEvent event) {
    try {
      eventProcessor = streamProcessor.onEvent(event);

      if (eventProcessor != null) {
        try {
          zeebeDb.transaction(eventProcessor::processEvent);
          metrics.incrementEventsProcessedCount();
          actor.runUntilDone(this::executeSideEffects);
        } catch (final RecoverableException recoverableException) {
          // recoverable
          LOG.error(
              ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING,
              event,
              getName(),
              recoverableException);
          actor.runDelayed(PROCESSING_RETRY_DELAY, () -> processEvent(currentEvent));
        } catch (final Exception e) {
          LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT, event, getName(), e);
          zeebeDb.transaction(() -> eventProcessor.processingFailed(e));
          // send rejection etc
          actor.runUntilDone(this::executeSideEffects);
        }
      } else {
        skipRecord();
      }
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_SKIP_EVENT, event, getName(), e);
      eventProcessor = null;
      skipRecord();
    }
  }

  private void skipRecord() {
    actor.submit(readNextEvent);
    metrics.incrementEventsSkippedCount();
  }

  private void executeSideEffects() {
    try {
      final boolean success = eventProcessor.executeSideEffects();
      if (success) {
        actor.done();

        actor.runUntilDone(this::writeEvent);
      } else if (isOpened()) {
        // try again
        actor.yield();
      } else {
        actor.done();
      }
    } catch (final Exception e) {
      actor.done();
      LOG.error(ERROR_MESSAGE_EXECUTE_SITE_EFFECTS_FAILED, getName(), e);
      onFailure();
    }
  }

  private void writeEvent() {
    try {
      logStreamWriter
          .producerId(streamProcessorContext.getId())
          .sourceRecordPosition(currentEvent.getPosition());

      eventPosition = eventProcessor.writeEvent(logStreamWriter);

      if (eventPosition >= 0) {
        actor.done();

        metrics.incrementEventsWrittenCount();

        updateState();
      } else if (isOpened()) {
        // try again
        actor.yield();
      } else {
        actor.done();
      }
    } catch (final Exception e) {
      actor.done();
      LOG.error(ERROR_MESSAGE_WRITE_FAILED, getName(), e);
      onFailure();
    }
  }

  private void updateState() {
    try {
      lastSuccessfulProcessedEventPosition = currentEvent.getPosition();

      final boolean hasWrittenEvent = eventPosition > 0;
      if (hasWrittenEvent) {
        lastWrittenEventPosition = eventPosition;
      }

      // continue with next event
      eventProcessor = null;
      actor.submit(readNextEvent);
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_UPDATE_STATE_FAILED, getName(), e);
      onFailure();
    }
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
    if (currentEvent != null) {
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
    }

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

      snapshotPosition = lastSuccessfulProcessedEventPosition;
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

    streamProcessorContext.logStream.removeOnCommitPositionUpdatedCondition(
        onCommitPositionUpdatedCondition);
    onCommitPositionUpdatedCondition = null;
  }

  private void onFailure() {
    phase = Phase.FAILED;

    isOpened.set(false);

    actor.close();
  }

  public boolean isOpened() {
    return isOpened.get();
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
      actor.submit(readNextEvent);
    }
  }

  private enum Phase {
    REPROCESSING,
    PROCESSING,
    FAILED
  }
}
