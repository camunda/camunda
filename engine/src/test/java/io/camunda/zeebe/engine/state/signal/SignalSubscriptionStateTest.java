/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.signal;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.state.mutable.MutableSignalSubscriptionState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableReference;
import org.assertj.core.api.Assertions;
import org.assertj.core.groups.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class SignalSubscriptionStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableSignalSubscriptionState state;

  @Before
  public void setUp() {
    state = stateRule.getProcessingState().getSignalSubscriptionState();
  }

  @Test
  public void shouldExistAfterPut() {
    final SignalSubscriptionRecord subscription =
        createSubscription("signalName", "startEventID", 1);
    state.put(1L, subscription);
    assertThat(state.exists(subscription)).isTrue();
  }

  @Test
  public void shouldNotExistForDifferentKey() {
    final SignalSubscriptionRecord subscription =
        createSubscription("signalName", "startEventID", 1);
    state.put(1L, subscription);

    subscription.setProcessDefinitionKey(2);
    assertThat(state.exists(subscription)).isFalse();
  }

  @Test
  public void shouldStoreSubscriptionWithKey() {
    // given
    final SignalSubscriptionRecord subscription =
        createSubscription("signalName", "startEventID", 1);

    // then
    state.put(1L, subscription);

    // when
    final var storedSubscription = new MutableReference<SignalSubscription>();
    state.visitBySignalName(
        subscription.getSignalNameBuffer(), subscription.getTenantId(), storedSubscription::set);

    assertThat(storedSubscription).isNotNull();
    assertThat(storedSubscription.get().getKey()).isEqualTo(1L);
    assertThat(storedSubscription.get().getRecord()).isEqualTo(subscription);
  }

  @Test
  public void shouldVisitForSignalNames() {
    final SignalSubscriptionRecord subscription1 = createSubscription("signal", "startEvent1", 1);
    state.put(1L, subscription1);

    // more subscriptions for same signal
    final SignalSubscriptionRecord subscription2 = createSubscription("signal", "startEvent2", 2);
    state.put(2L, subscription2);

    final SignalSubscriptionRecord subscription3 = createSubscription("signal", "startEvent3", 3);
    state.put(3L, subscription3);

    // should not visit other signal
    final SignalSubscriptionRecord subscription4 =
        createSubscription("signal-other", "startEvent4", 3);
    state.put(4L, subscription4);

    final List<String> visitedStartEvents = new ArrayList<>();

    state.visitBySignalName(
        wrapString("signal"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        subscription ->
            visitedStartEvents.add(
                bufferAsString(subscription.getRecord().getCatchEventIdBuffer())));

    assertThat(visitedStartEvents.size()).isEqualTo(3);
    assertThat(visitedStartEvents)
        .containsExactlyInAnyOrder("startEvent1", "startEvent2", "startEvent3");
  }

  @Test
  public void shouldVisitForProcessDefinitionKey() {
    final SignalSubscriptionRecord subscription1 = createSubscription("signal1", "startEvent1", 1);
    state.put(1L, subscription1);

    // more subscriptions for same process definition
    final SignalSubscriptionRecord subscription2 = createSubscription("signal2", "startEvent2", 1);
    state.put(2L, subscription2);

    // should not visit with other process definition
    final SignalSubscriptionRecord subscription3 = createSubscription("signal3", "startEvent3", 2);
    state.put(3L, subscription3);

    final List<Tuple> visitedSubscriptions = new ArrayList<>();

    state.visitStartEventSubscriptionsByProcessDefinitionKey(
        1,
        subscription ->
            visitedSubscriptions.add(
                tuple(subscription.getKey(), subscription.getRecord().getSignalName())));

    assertThat(visitedSubscriptions)
        .hasSize(2)
        .containsExactlyInAnyOrder(tuple(1L, "signal1"), tuple(2L, "signal2"));
  }

  @Test
  public void shouldNotExistAfterRemove() {
    final SignalSubscriptionRecord subscription1 = createSubscription("signal1", "startEvent1", 1);
    state.put(1L, subscription1);

    final SignalSubscriptionRecord subscription2 = createSubscription("signal2", "startEvent2", 2);
    state.put(2L, subscription2);

    state.remove(1L, wrapString("signal1"), TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    state.remove(2L, wrapString("signal2"), TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isFalse();
  }

  @Test
  public void shouldNotRemoveOtherSubscriptions() {
    final SignalSubscriptionRecord subscription1 = createSubscription("signal1", "startEvent1", 1);
    state.put(1L, subscription1);

    final SignalSubscriptionRecord subscription2 = createSubscription("signal2", "startEvent2", 1);
    state.put(2L, subscription2);

    final SignalSubscriptionRecord subscription3 = createSubscription("signal1", "startEvent1", 2);
    state.put(3L, subscription3);

    state.remove(1L, wrapString("signal1"), TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(state.exists(subscription1)).isFalse();
    assertThat(state.exists(subscription2)).isTrue();
    assertThat(state.exists(subscription3)).isTrue();
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final SignalSubscriptionRecord writtenRecord = createSubscription("signal", "start", key);

    // when
    state.put(key, writtenRecord);
    writtenRecord.setSignalName(BufferUtil.wrapString("foo"));

    // then
    state.visitBySignalName(
        BufferUtil.wrapString("signal"),
        TenantOwned.DEFAULT_TENANT_IDENTIFIER,
        readRecord -> {
          assertThat(readRecord.getRecord().getSignalNameBuffer())
              .isNotEqualTo(writtenRecord.getSignalNameBuffer());
          assertThat(readRecord.getRecord().getSignalNameBuffer())
              .isEqualTo(BufferUtil.wrapString("signal"));
          Assertions.assertThat(writtenRecord.getSignalNameBuffer())
              .isEqualTo(BufferUtil.wrapString("foo"));
        });

    final SignalSubscriptionRecord secondSub = createSubscription("signal", "start", 23);
    state.exists(secondSub);
    Assertions.assertThat(writtenRecord.getSignalNameBuffer())
        .isEqualTo(BufferUtil.wrapString("foo"));
  }

  private SignalSubscriptionRecord createSubscription(
      final String signalName, final String startEventId, final long key) {
    return new SignalSubscriptionRecord()
        .setCatchEventId(wrapString(startEventId))
        .setSignalName(wrapString(signalName))
        .setProcessDefinitionKey(key);
  }
}
