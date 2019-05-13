/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

import io.zeebe.db.DbContext;
import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.retry.EndlessRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Represents the reprocessing state machine, which is executed on reprocessing.
 *
 * <pre>
 * +------------------+   +-------------+           +------------------------+
 * |                  |   |             |           |                        |
 * |  startRecover()  |--->  scanLog()  |---------->|  reprocessNextEvent()  |
 * |                  |   |             |           |                        |
 * +------------------+   +---+---------+           +-----^------+-----------+
 *                            |                           |      |
 * +-----------------+        | no source events          |      |
 * |                 |        |                           |      |
 * |  onRecovered()  <--------+                           |      |    +--------------------+
 * |                 |                                    |      |    |                    |
 * +--------^--------+                hasNext             |      +--->|  reprocessEvent()  |
 *          |            +--------------------------------+           |                    |
 *          |            |                                            +----+----------+----+
 *          |            |                                                 |          |
 *   +------+------------+-----+                                           |          |
 *   |                         |               no event processor          |          |
 *   |  onRecordReprocessed()  |<------------------------------------------+          |
 *   |                         |                                                      |
 *   +---------^---------------+                                                      |
 *             |                                                                      |
 *             |      +--------------------------+       +----------------------+     |
 *             |      |                          |       |                      |     |
 *             +------+  updateStateUntilDone()  <-------+  processUntilDone()  |<----+
 *                    |                          |       |                      |
 *                    +------^------------+------+       +---^------------+-----+
 *                           |            |                  |            |
 *                           +------------+                  +------------+
 *                             exception                       exception
 * </pre>
 *
 * See https://textik.com/#773271ce7ea2096a
 */
public final class ReProcessingStateMachine {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;

  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{}' with processor '{}', but caught an exception. Skip this event.";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_SOURCE_EVENT =
      "Expected to find last source event position '%d', but last position was '%d'. Failed to reprocess on processor '%s'";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT =
      "Expected to find last source event position '%d', but found no next event. Failed to reprocess on processor '%s'";

  private static final String LOG_STMT_REPROCESSING_FINISHED =
      "Processor {} finished reprocessing at event position {}";
  private static final String LOG_STMT_FAILED_ON_PROCESSING =
      "Event {} failed on processing last time, will call #onError to update workflow instance blacklist.";

  private final int producerId;

  public static ReprocessingStateMachineBuilder builder() {
    return new ReprocessingStateMachineBuilder();
  }

  private final ActorControl actor;
  private final String streamProcessorName;
  private final StreamProcessor streamProcessor;
  private final EventFilter eventFilter;
  private final LogStreamReader logStreamReader;

  private final DbContext dbContext;
  private final RetryStrategy updateStateRetryStrategy;
  private final RetryStrategy processRetryStrategy;

  private final BooleanSupplier abortCondition;
  private final Set<Long> failedEventPositions = new HashSet<>();

  private ReProcessingStateMachine(
      StreamProcessorContext context,
      StreamProcessor streamProcessor,
      DbContext dbContext,
      BooleanSupplier abortCondition) {
    this.actor = context.getActorControl();
    this.streamProcessorName = context.getName();
    this.eventFilter = context.getEventFilter();
    this.logStreamReader = context.getLogStreamReader();
    this.producerId = context.getId();

    this.streamProcessor = streamProcessor;
    this.dbContext = dbContext;
    this.updateStateRetryStrategy = new EndlessRetryStrategy(actor);
    this.processRetryStrategy = new EndlessRetryStrategy(actor);
    this.abortCondition = abortCondition;
  }

  // current iteration
  private long lastSourceEventPosition;
  private ActorFuture<Void> recoveryFuture;
  private LoggedEvent currentEvent;
  private EventProcessor eventProcessor;
  private ZeebeDbTransaction zeebeDbTransaction;

  ActorFuture<Void> startRecover(final long snapshotPosition) {
    recoveryFuture = new CompletableActorFuture<>();

    final long startPosition = logStreamReader.getPosition();

    LOG.info("Start scanning the log for error events.");
    lastSourceEventPosition = scanLog(snapshotPosition);
    LOG.info("Finished scanning the log for error events.");

    if (lastSourceEventPosition > snapshotPosition) {
      LOG.info(
          "Processor {} starts reprocessing, until last source event position {}",
          streamProcessorName,
          lastSourceEventPosition);
      logStreamReader.seek(startPosition);
      reprocessNextEvent();
    } else {
      recoveryFuture.complete(null);
    }
    return recoveryFuture;
  }

  private long scanLog(final long snapshotPosition) {
    long lastSourceEventPosition = -1L;

    if (logStreamReader.hasNext()) {
      lastSourceEventPosition = snapshotPosition;
      while (logStreamReader.hasNext()) {
        final LoggedEvent newEvent = logStreamReader.next();

        final long errorPosition = streamProcessor.getFailedPosition(newEvent);
        if (errorPosition >= 0) {
          LOG.debug(
              "Found error-prone event {} on reprocessing, will add position {} to the blacklist.",
              newEvent,
              errorPosition);
          failedEventPositions.add(errorPosition);
        }

        // ignore events from other producers
        if (newEvent.getProducerId() == producerId) {
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

  private void readNextEvent() {
    if (!logStreamReader.hasNext()) {
      throw new IllegalStateException(
          String.format(
              ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT,
              lastSourceEventPosition,
              streamProcessorName));
    }

    currentEvent = logStreamReader.next();
    if (currentEvent.getPosition() > lastSourceEventPosition) {
      throw new IllegalStateException(
          String.format(
              ERROR_MESSAGE_REPROCESSING_NO_SOURCE_EVENT,
              lastSourceEventPosition,
              currentEvent.getPosition(),
              streamProcessorName));
    }
  }

  private void reprocessNextEvent() {
    try {
      readNextEvent();

      if (eventFilter == null || eventFilter.applies(currentEvent)) {
        reprocessEvent(currentEvent);
      } else {
        onRecordReprocessed(currentEvent);
      }

    } catch (final RuntimeException e) {
      recoveryFuture.completeExceptionally(e);
    }
  }

  private void reprocessEvent(final LoggedEvent currentEvent) {
    try {
      eventProcessor = streamProcessor.onEvent(currentEvent);
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, currentEvent, streamProcessorName, e);
    }

    if (eventProcessor == null) {
      onRecordReprocessed(currentEvent);
      return;
    }

    processUntilDone(currentEvent);
  }

  private void processUntilDone(LoggedEvent currentEvent) {
    final TransactionOperation operationOnProcessing = chooseOperationForEvent(currentEvent);

    final ActorFuture<Boolean> resultFuture =
        processRetryStrategy.runWithRetry(
            () -> {
              final boolean onRetry = zeebeDbTransaction != null;
              if (onRetry) {
                zeebeDbTransaction.rollback();
              }
              zeebeDbTransaction = dbContext.getCurrentTransaction();
              zeebeDbTransaction.run(operationOnProcessing);
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        resultFuture,
        (v, t) -> {
          // processing should be retried endless until it worked
          assert t == null : "On reprocessing there shouldn't be any exception thrown.";
          updateStateUntilDone();
        });
  }

  private TransactionOperation chooseOperationForEvent(LoggedEvent currentEvent) {
    final TransactionOperation operationOnProcessing;
    if (failedEventPositions.contains(currentEvent.getPosition())) {
      LOG.info(LOG_STMT_FAILED_ON_PROCESSING, currentEvent);
      operationOnProcessing =
          () -> eventProcessor.onError(new Exception("Failed on last processing."));
    } else {
      operationOnProcessing = eventProcessor::processEvent;
    }
    return operationOnProcessing;
  }

  private void updateStateUntilDone() {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.commit();
              zeebeDbTransaction = null;
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          // update state should be retried endless until it worked
          assert throwable == null : "On reprocessing there shouldn't be any exception thrown.";
          onRecordReprocessed(currentEvent);
        });
  }

  private void onRecordReprocessed(final LoggedEvent currentEvent) {
    if (currentEvent.getPosition() == lastSourceEventPosition) {
      LOG.info(LOG_STMT_REPROCESSING_FINISHED, streamProcessorName, currentEvent.getPosition());
      onRecovered();
    } else {
      actor.submit(this::reprocessNextEvent);
    }
  }

  private void onRecovered() {
    recoveryFuture.complete(null);
    failedEventPositions.clear();
  }

  public static class ReprocessingStateMachineBuilder {

    private StreamProcessorContext streamProcessorContext;
    private DbContext dbContext;
    private StreamProcessor streamProcessor;
    private BooleanSupplier abortCondition;

    public ReprocessingStateMachineBuilder setStreamProcessor(StreamProcessor streamProcessor) {
      this.streamProcessor = streamProcessor;
      return this;
    }

    public ReprocessingStateMachineBuilder setStreamProcessorContext(
        StreamProcessorContext context) {
      this.streamProcessorContext = context;
      return this;
    }

    public ReprocessingStateMachineBuilder setDbContext(DbContext dbContext) {
      this.dbContext = dbContext;
      return this;
    }

    public ReprocessingStateMachineBuilder setAbortCondition(BooleanSupplier abortCondition) {
      this.abortCondition = abortCondition;
      return this;
    }

    public ReProcessingStateMachine build() {
      Objects.requireNonNull(streamProcessorContext);
      Objects.requireNonNull(streamProcessor);
      Objects.requireNonNull(dbContext);
      Objects.requireNonNull(abortCondition);
      return new ReProcessingStateMachine(
          streamProcessorContext, streamProcessor, dbContext, abortCondition);
    }
  }
}
