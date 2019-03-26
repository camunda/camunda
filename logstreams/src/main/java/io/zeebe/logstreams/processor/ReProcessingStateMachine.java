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
import io.zeebe.db.ZeebeDbTransaction;
import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.util.exception.RecoverableException;
import io.zeebe.util.retry.RecoverableRetryStrategy;
import io.zeebe.util.retry.RetryStrategy;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Represents the reprocessing state machine, which is executed on reprocessing.
 *
 * <pre>
 *   +------------------+
 *   |                  |
 *   |  startRecover()  |
 *   |                  |               +--------------------+
 *   +------------------+               |                    |
 *            |                   +---->|  reprocessEvent()  |--------+
 *            v                   |     |                    |        |
 * +------------------------+     |     +----+---------------+        | exception
 * |                        |     |          |                        |
 * |  reprocessNextEvent()  |-----+          |                        |
 * |                        |                |                 +------v------+
 * +------------------------+                |                 |             |-------+
 *            ^                              |        +-------->  onError()  |       | exception
 *            |                              |        |        |             |<------+
 *            | hasNext                      |        |        +----------+--+
 *            |                              |        |                   |
 * +----------+--------------+               |        | exception         |
 * |                         |               |        |                   |
 * |  onRecordReprocessed()  <------+        |        |                   |
 * |                         |      |        |        |                   |
 * +-----------+-------------+      |   +----v--------+---+               |
 *             |                    |   |                 |               |
 *             |                    +---|  updateState()  |<--------------+
 *             |                        |                 |
 *             v                        +-----------------+
 *    +-----------------+
 *    |                 |
 *    |  onRecovered()  |
 *    |                 |
 *    +-----------------+
 * </pre>
 *
 * See https://textik.com/#773271ce7ea2096a
 */
public final class ReProcessingStateMachine {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;

  private static final String ERROR_MESSAGE_ROLLBACK_ABORTED =
      "Expected to roll back the current transaction for event '{}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_UPDATE_STATE_FAILED =
      "Expected to successfully update state for event '{}' with processor '{}', but caught an exception. Retry.";
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find event processor for event '{}' with processor '{}', but caught an exception. Skip this event.";

  private static final String ERROR_MESSAGE_REPROCESSING_NO_SOURCE_EVENT =
      "Expected to find last source event position '%d', but last position was '%d'. Failed to reprocess on processor '%s'";
  private static final String ERROR_MESSAGE_REPROCESSING_NO_NEXT_EVENT =
      "Expected to find last source event position '%d', but found no next event. Failed to reprocess on processor '%s'";
  private static final String ERROR_MESSAGE_REPROCESSING_FAILED_SKIP_EVENT =
      "Expected to successfully reprocess event '{}' on processor '{}', but caught an exception.";
  private static final String ERROR_MESSAGE_REPROCESSING_FAILED_RETRY_REPROCESSING =
      "Expected to reprocess event '{}' successfully on stream processor '{}', but caught recoverable exception. Retry reprocessing.";

  private static final String LOG_STMT_REPROCESSING_FINISHED =
      "Processor {} finished reprocessing at event position {}";

  private static final Duration PROCESSING_RETRY_DELAY = Duration.ofMillis(250);

  public static ProcessingStateMachineBuilder builder() {
    return new ProcessingStateMachineBuilder();
  }

  private final ActorControl actor;
  private final String streamProcessorName;
  private final StreamProcessor streamProcessor;
  private final EventFilter eventFilter;
  private final LogStreamReader logStreamReader;

  private final DbContext dbContext;
  private final RetryStrategy updateStateRetryStrategy;

  private final BooleanSupplier abortCondition;

  private ReProcessingStateMachine(
      StreamProcessorContext context,
      StreamProcessor streamProcessor,
      DbContext dbContext,
      BooleanSupplier abortCondition) {
    this.actor = context.getActorControl();
    this.streamProcessorName = context.getName();
    this.eventFilter = context.getEventFilter();
    this.logStreamReader = context.getLogStreamReader();

    this.streamProcessor = streamProcessor;
    this.dbContext = dbContext;
    this.updateStateRetryStrategy = new RecoverableRetryStrategy(actor);
    this.abortCondition = abortCondition;
  }

  // current iteration
  private long lastSourceEventPosition;
  private ActorFuture<Void> recoveryFuture;

  private LoggedEvent currentEvent;
  private EventProcessor eventProcessor;
  private ZeebeDbTransaction zeebeDbTransaction;

  ActorFuture<Void> startRecover(long lastSourceEventPosition) {
    this.lastSourceEventPosition = lastSourceEventPosition;
    recoveryFuture = new CompletableActorFuture<>();
    reprocessNextEvent();
    return recoveryFuture;
  }

  private void reprocessNextEvent() {
    try {
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

    try {
      // don't execute side effects or write events
      zeebeDbTransaction = dbContext.getCurrentTransaction();
      zeebeDbTransaction.run(eventProcessor::processEvent);
      updateState();
    } catch (final RecoverableException recoverableException) {
      // recoverable
      LOG.error(
          ERROR_MESSAGE_REPROCESSING_FAILED_RETRY_REPROCESSING,
          currentEvent,
          streamProcessorName,
          recoverableException);
      actor.runDelayed(PROCESSING_RETRY_DELAY, () -> reprocessEvent(currentEvent));
      return;
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_REPROCESSING_FAILED_SKIP_EVENT, currentEvent, streamProcessorName, e);
      onProcessingError(e, this::updateState);
    }
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
  }

  private void onProcessingError(Throwable t, Runnable nextStep) {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.rollback();
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_ROLLBACK_ABORTED, currentEvent, throwable);
          }
          try {
            zeebeDbTransaction = dbContext.getCurrentTransaction();
            zeebeDbTransaction.run(() -> eventProcessor.onError(t));
            nextStep.run();
          } catch (Exception ex) {
            onProcessingError(ex, nextStep);
          }
        });
  }

  private void updateState() {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.commit();
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(
                ERROR_MESSAGE_UPDATE_STATE_FAILED, currentEvent, streamProcessorName, throwable);
            onProcessingError(throwable, this::updateState);
          } else {
            onRecordReprocessed(currentEvent);
          }
        });
  }

  public static class ProcessingStateMachineBuilder {

    private StreamProcessor streamProcessor;

    private StreamProcessorContext streamProcessorContext;
    private DbContext dbContext;
    private BooleanSupplier abortCondition;

    public ProcessingStateMachineBuilder setStreamProcessor(StreamProcessor streamProcessor) {
      this.streamProcessor = streamProcessor;
      return this;
    }

    public ProcessingStateMachineBuilder setStreamProcessorContext(StreamProcessorContext context) {
      this.streamProcessorContext = context;
      return this;
    }

    public ProcessingStateMachineBuilder setDbContext(DbContext dbContext) {
      this.dbContext = dbContext;
      return this;
    }

    public ProcessingStateMachineBuilder setAbortCondition(BooleanSupplier abortCondition) {
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
