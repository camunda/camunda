/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.layered.zdb.LayeredDomain;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDb;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbFactory;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.engine.state.instance.DbJobState;
import io.camunda.zeebe.engine.state.instance.DbTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.LayeredViewJobState;
import io.camunda.zeebe.engine.state.instance.LayeredViewTimerInstanceState;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.message.LayeredViewMessageState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Encoding parity between the owner-side Db states and the view-backed states the asynchronous
 * checkers use when the layered-state flag is on: what the engine writes through its layered
 * context must be visible — after a freeze — through {@link LayeredViewTimerInstanceState}, {@link
 * LayeredViewMessageState} and {@link LayeredViewJobState}, which re-derive the same keys against
 * the same stores.
 */
final class LayeredViewScheduledTaskStateTest {

  @TempDir private File dbDirectory;

  private ZeebeDb<ZbColumnFamilies> db;
  private LayeredZeebeDb<ZbColumnFamilies> layered;
  private TransactionContext context;
  private LayeredDomain domain;
  private DbTimerInstanceState timerState;
  private DbMessageState messageState;
  private DbJobState jobState;

  @BeforeEach
  void setUp() {
    final var layeredFactory =
        LayeredZeebeDbFactory.of(
            DefaultZeebeDbFactory.defaultFactory(),
            LayeredZeebeDbConfig.defaults(),
            ZbColumnFamilies.class);
    db = layeredFactory.createDb(dbDirectory);
    layered = (LayeredZeebeDb<ZbColumnFamilies>) db;
    context = layered.layeredContext();
    timerState = new DbTimerInstanceState(layered, context);
    messageState = new DbMessageState(layered, context, 1);
    jobState = new DbJobState(layered, context);
    domain = layered.defaultDomain();
    domain.coordinator();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(db);
  }

  @Test
  void shouldSeeCommittedTimersAfterFreeze() {
    // given committed timers buffered in the layered store (not yet persisted)
    context.runInTransaction(
        () -> {
          storeTimer(1L, 11L, 1_000L);
          storeTimer(2L, 22L, 2_000L);
          storeTimer(3L, 33L, 5_000L);
        });
    domain.freezeNow(1L);

    // when scanning through a read view
    final var viewState = new LayeredViewTimerInstanceState(domain.viewPublisher());
    final List<Long> visitedTimerKeys = new ArrayList<>();
    final long nextDueDate =
        viewState.processTimersWithDueDateBefore(
            3_000L,
            timer -> {
              visitedTimerKeys.add(timer.getKey());
              return true;
            });

    // then the due timers are visited in due-date order and the next wake-up is re-derived
    assertThat(visitedTimerKeys).containsExactly(11L, 22L);
    assertThat(nextDueDate).isEqualTo(5_000L);
  }

  @Test
  void shouldNotSeeTimersCommittedAfterTheFreeze() {
    // given
    context.runInTransaction(() -> storeTimer(1L, 11L, 1_000L));
    domain.freezeNow(1L);
    context.runInTransaction(() -> storeTimer(2L, 22L, 1_500L));

    // when scanning through a read view built from the first freeze
    final var viewState = new LayeredViewTimerInstanceState(domain.viewPublisher());
    final List<Long> visitedTimerKeys = new ArrayList<>();
    viewState.processTimersWithDueDateBefore(
        3_000L,
        timer -> {
          visitedTimerKeys.add(timer.getKey());
          return true;
        });

    // then only the frozen timer is visible; the next freeze makes the second one visible
    assertThat(visitedTimerKeys).containsExactly(11L);
    domain.freezeNow(2L);
    visitedTimerKeys.clear();
    viewState.processTimersWithDueDateBefore(
        3_000L,
        timer -> {
          visitedTimerKeys.add(timer.getKey());
          return true;
        });
    assertThat(visitedTimerKeys).containsExactly(11L, 22L);
  }

  @Test
  void shouldStopTimerScanWhenVisitorDeclines() {
    // given
    context.runInTransaction(
        () -> {
          storeTimer(1L, 11L, 1_000L);
          storeTimer(2L, 22L, 2_000L);
        });
    domain.freezeNow(1L);

    // when the visitor declines the first due timer (e.g. the task result is full)
    final var viewState = new LayeredViewTimerInstanceState(domain.viewPublisher());
    final long nextDueDate = viewState.processTimersWithDueDateBefore(3_000L, timer -> false);

    // then the declined timer's due date is the next wake-up
    assertThat(nextDueDate).isEqualTo(1_000L);
  }

  @Test
  void shouldSeeCommittedMessageDeadlinesAfterFreeze() {
    // given a committed message with a deadline, buffered in the layered store
    context.runInTransaction(() -> messageState.put(17L, createMessage(4_000L)));
    domain.freezeNow(1L);
    final var viewState = new LayeredViewMessageState(domain.viewPublisher());

    // when scanning through a read view
    final List<Long> visitedMessageKeys = new ArrayList<>();
    final boolean stoppedBeforeExpired =
        viewState.visitMessagesWithDeadlineBeforeTimestamp(
            5_000L,
            null,
            (deadline, messageKey) -> {
              visitedMessageKeys.add(messageKey);
              return false;
            });
    final boolean stoppedAfterExpiry =
        viewState.visitMessagesWithDeadlineBeforeTimestamp(
            3_000L, null, (deadline, messageKey) -> false);

    // then the expired deadline is found exactly when the timestamp passed it
    assertThat(stoppedBeforeExpired).isTrue();
    assertThat(visitedMessageKeys).containsExactly(17L);
    assertThat(stoppedAfterExpiry).isFalse();
  }

  @Test
  void shouldSeeCommittedJobDeadlinesAfterFreeze() {
    // given committed activated jobs buffered in the layered store (not yet persisted)
    context.runInTransaction(
        () -> {
          storeActivatedJob(11L, 1_000L);
          storeActivatedJob(22L, 2_000L);
          storeActivatedJob(33L, 5_000L);
        });
    domain.freezeNow(1L);

    // when scanning through a read view
    final var viewState = new LayeredViewJobState(domain.viewPublisher());
    final List<Long> visitedJobKeys = new ArrayList<>();
    final DeadlineIndex lastVisited =
        viewState.forEachTimedOutEntry(
            3_000L,
            null,
            (key, record) -> {
              visitedJobKeys.add(key);
              return true;
            });

    // then the timed-out jobs are visited in deadline order and the scan ran to completion
    assertThat(visitedJobKeys).containsExactly(11L, 22L);
    assertThat(lastVisited).isNull();
  }

  @Test
  void shouldResumeJobDeadlineScanAtTheYieldedIndex() {
    // given committed activated jobs and a scan that yielded (e.g. the task result was full)
    context.runInTransaction(
        () -> {
          storeActivatedJob(11L, 1_000L);
          storeActivatedJob(22L, 2_000L);
          storeActivatedJob(33L, 2_500L);
        });
    domain.freezeNow(1L);
    final var viewState = new LayeredViewJobState(domain.viewPublisher());
    final DeadlineIndex yieldedAt =
        viewState.forEachTimedOutEntry(3_000L, null, (key, record) -> key != 22L);
    assertThat(yieldedAt).isEqualTo(new DeadlineIndex(2_000L, 22L));

    // when resuming from the yielded index
    final List<Long> visitedJobKeys = new ArrayList<>();
    final DeadlineIndex lastVisited =
        viewState.forEachTimedOutEntry(
            3_000L,
            yieldedAt,
            (key, record) -> {
              visitedJobKeys.add(key);
              return true;
            });

    // then the scan re-visits the yielded entry (resume is inclusive, matching the owner path's
    // seek) and continues to the end without re-visiting earlier entries
    assertThat(visitedJobKeys).containsExactly(22L, 33L);
    assertThat(lastVisited).isNull();
  }

  @Test
  void shouldNotSeeJobDeadlinesCommittedAfterTheFreeze() {
    // given
    context.runInTransaction(() -> storeActivatedJob(11L, 1_000L));
    domain.freezeNow(1L);
    context.runInTransaction(() -> storeActivatedJob(22L, 1_500L));

    // when scanning through a read view built from the first freeze
    final var viewState = new LayeredViewJobState(domain.viewPublisher());
    final List<Long> visitedJobKeys = new ArrayList<>();
    viewState.forEachTimedOutEntry(
        3_000L,
        null,
        (key, record) -> {
          visitedJobKeys.add(key);
          return true;
        });

    // then only the frozen job is visible; the next freeze makes the second one visible
    assertThat(visitedJobKeys).containsExactly(11L);
    domain.freezeNow(2L);
    visitedJobKeys.clear();
    viewState.forEachTimedOutEntry(
        3_000L,
        null,
        (key, record) -> {
          visitedJobKeys.add(key);
          return true;
        });
    assertThat(visitedJobKeys).containsExactly(11L, 22L);
  }

  @Test
  void shouldRejectReadsTheCheckersNeverIssue() {
    // given
    final var timerViewState = new LayeredViewTimerInstanceState(domain.viewPublisher());
    final var messageViewState = new LayeredViewMessageState(domain.viewPublisher());
    final var jobViewState = new LayeredViewJobState(domain.viewPublisher());

    // when / then
    assertThatThrownBy(() -> timerViewState.get(1L, 2L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> messageViewState.getMessage(1L))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> jobViewState.getJob(1L))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private void storeTimer(final long elementInstanceKey, final long timerKey, final long dueDate) {
    final TimerInstance timer = new TimerInstance();
    timer.setElementInstanceKey(elementInstanceKey);
    timer.setKey(timerKey);
    timer.setDueDate(dueDate);
    timerState.store(timer);
  }

  private void storeActivatedJob(final long jobKey, final long deadline) {
    final JobRecord job =
        new JobRecord().setType("order-shipment").setRetries(2).setDeadline(deadline);
    jobState.create(jobKey, job);
    jobState.activate(jobKey, job);
  }

  private MessageRecord createMessage(final long deadline) {
    return new MessageRecord()
        .setName("order-shipped")
        .setCorrelationKey("order-4711")
        .setTimeToLive(deadline)
        .setDeadline(deadline);
  }
}
