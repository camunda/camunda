/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hub;

import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.APPLIED;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.APPLYING;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.BUFFERING;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.CANCELED;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.ERROR;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventStatus.REJECTED;
import static io.camunda.zeebe.engine.state.immutable.HubEventState.HubEventType.POLICY_UPDATE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.immutable.HubEventState.HubEvent;
import io.camunda.zeebe.engine.state.mutable.MutableHubEventState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbHubEventStateTest {

  private MutableProcessingState processingState;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableHubEventState hubEventState;

  @BeforeEach
  void setUp() {
    hubEventState = processingState.getHubEventState();
  }

  @Test
  void shouldHaveNoEventBeforeTheFirstOne() {
    // when
    final var last = hubEventState.getLast();

    // then
    assertThat(last).isEmpty();
    assertThat(hubEventState.getLastFinished()).isEmpty();
    assertThat(hubEventState.getLastAppliedSeqNo()).isEmpty();
  }

  @Test
  void shouldReturnAnAppliedEventWithoutReason() {
    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);

    // then
    assertThat(hubEventState.getLast()).contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
    assertThat(hubEventState.getLastAppliedSeqNo()).hasValue(7L);
  }

  @Test
  void shouldReturnARejectedEventWithItsReason() {
    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, REJECTED, "no such tenant");

    // then
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(7L, POLICY_UPDATE, REJECTED, "no such tenant"));
    assertThat(hubEventState.getLastAppliedSeqNo()).isEmpty();
  }

  @Test
  void shouldReturnTheTypeOfTheEvent() {
    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(hubEventState.getLast()).map(HubEvent::type).contains(POLICY_UPDATE);
  }

  @Test
  void shouldNotFinishAnEventInProgress() {
    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(hubEventState.getLast()).contains(new HubEvent(7L, POLICY_UPDATE, BUFFERING, null));
    assertThat(hubEventState.getLastFinished()).isEmpty();
  }

  @Test
  void shouldMoveAnEventFromBufferingOverApplyingToApplied() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, BUFFERING, null);
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLYING, null);

    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);

    // then
    assertThat(storedEventCount()).isOne();
    assertThat(hubEventState.getLast()).contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
  }

  @Test
  void shouldKeepTheLastFinishedEventWhileTheNextIsInProgress() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);

    // when
    hubEventState.setStatus(8L, POLICY_UPDATE, APPLYING, null);

    // then
    assertThat(hubEventState.getLast()).contains(new HubEvent(8L, POLICY_UPDATE, APPLYING, null));
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
  }

  @Test
  void shouldIgnoreAnEventAtOrBelowTheLastFinishedSequence() {
    // given
    hubEventState.setStatus(8L, POLICY_UPDATE, REJECTED, "no such tenant");

    // when
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(storedEventCount()).isOne();
    assertThat(hubEventState.getLast())
        .contains(new HubEvent(8L, POLICY_UPDATE, REJECTED, "no such tenant"));
  }

  @Test
  void shouldTryAnEventInErrorAgain() {
    // given
    hubEventState.setStatus(8L, POLICY_UPDATE, ERROR, "timeout");

    // when
    hubEventState.setStatus(8L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(storedEventCount()).isOne();
    assertThat(hubEventState.getLast()).contains(new HubEvent(8L, POLICY_UPDATE, BUFFERING, null));
  }

  @Test
  void shouldTryACanceledEventAgain() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, BUFFERING, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, CANCELED, "hangs");

    // when
    hubEventState.setStatus(8L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(storedEventCount()).isEqualTo(2);
    assertThat(hubEventState.getLast()).contains(new HubEvent(8L, POLICY_UPDATE, BUFFERING, null));
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
  }

  @Test
  void shouldReplaceAnEventInErrorWithALaterOne() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, ERROR, "timeout");

    // when
    hubEventState.setStatus(9L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(storedEventCount()).isEqualTo(2);
    assertThat(hubEventState.getLast()).contains(new HubEvent(9L, POLICY_UPDATE, BUFFERING, null));
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(7L, POLICY_UPDATE, APPLIED, null));
  }

  @Test
  void shouldKeepTheLastAppliedTheLastRejectedAndTheEventInProgress() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, REJECTED, "no such tenant");

    // when
    hubEventState.setStatus(9L, POLICY_UPDATE, BUFFERING, null);

    // then
    assertThat(storedEventCount()).isEqualTo(3);
    assertThat(hubEventState.getLast()).contains(new HubEvent(9L, POLICY_UPDATE, BUFFERING, null));
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(8L, POLICY_UPDATE, REJECTED, "no such tenant"));
    assertThat(hubEventState.getLastAppliedSeqNo()).hasValue(7L);
  }

  @Test
  void shouldKeepOnlyTheLastAppliedAndTheLastRejectedEventAfterARejection() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, REJECTED, "no such tenant");
    hubEventState.setStatus(9L, POLICY_UPDATE, BUFFERING, null);

    // when
    hubEventState.setStatus(9L, POLICY_UPDATE, REJECTED, "no such role");

    // then
    assertThat(storedEventCount()).isEqualTo(2);
    assertThat(hubEventState.getLastFinished())
        .contains(new HubEvent(9L, POLICY_UPDATE, REJECTED, "no such role"));
    assertThat(hubEventState.getLastAppliedSeqNo()).hasValue(7L);
  }

  @Test
  void shouldKeepOnlyTheAppliedEventAfterAnApply() {
    // given
    hubEventState.setStatus(7L, POLICY_UPDATE, APPLIED, null);
    hubEventState.setStatus(8L, POLICY_UPDATE, REJECTED, "no such tenant");
    hubEventState.setStatus(9L, POLICY_UPDATE, APPLYING, null);

    // when
    hubEventState.setStatus(9L, POLICY_UPDATE, APPLIED, null);

    // then
    assertThat(storedEventCount()).isOne();
    assertThat(hubEventState.getLast()).contains(new HubEvent(9L, POLICY_UPDATE, APPLIED, null));
  }

  @Test
  void shouldRecordNothingOnAnotherPartitionThanPartitionOne() {
    // given
    final var otherPartitionState = new DbHubEventState(2, zeebeDb, transactionContext);

    // when
    otherPartitionState.setStatus(7L, POLICY_UPDATE, APPLIED, null);

    // then
    assertThat(otherPartitionState.getLast()).isEmpty();
    assertThat(otherPartitionState.getLastAppliedSeqNo()).isEmpty();
  }

  /** How many events the column family holds; it must stay bounded. */
  private long storedEventCount() {
    return zeebeDb
        .createColumnFamily(
            ZbColumnFamilies.HUB_EVENT, transactionContext, new DbLong(), new PersistedHubEvent())
        .count();
  }
}
