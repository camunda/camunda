/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbElementInstanceStateTrackedTakenSequenceFlowKeysTest {

  private static final long FLOW_SCOPE_KEY = 1L;
  private static final long OTHER_FLOW_SCOPE_KEY = 2L;

  @SuppressWarnings("unused") // injected by the extension
  private MutableProcessingState processingState;

  @SuppressWarnings("unused") // injected by the extension
  private ZeebeDb<ZbColumnFamilies> zeebeDb;

  @SuppressWarnings("unused") // injected by the extension
  private TransactionContext transactionContext;

  private final DbLong flowScopeKey = new DbLong();

  private MutableElementInstanceState elementInstanceState;
  private ColumnFamily<DbLong, PersistedTakenSequenceFlowKeys>
      takenSequenceFlowKeysByScopeKeyColumnFamily;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    takenSequenceFlowKeysByScopeKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TAKEN_SEQUENCE_FLOW_KEYS_BY_SCOPE_KEY,
            transactionContext,
            flowScopeKey,
            new PersistedTakenSequenceFlowKeys());
  }

  @Test
  void shouldTrackTakenSequenceFlowKeysPerScopeAndDeleteThemOnRemoveInstance() {
    // given
    elementInstanceState.newInstance(
        FLOW_SCOPE_KEY, new ProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-1");
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-2");

    assertThat(getTrackedTakenSequenceFlowCount(FLOW_SCOPE_KEY)).isEqualTo(2);
    assertThat(getTrackedTakenSequenceFlowKeys(FLOW_SCOPE_KEY))
        .containsExactlyInAnyOrder("gateway-1->flow-1", "gateway-1->flow-2");

    // when
    elementInstanceState.removeInstance(FLOW_SCOPE_KEY);

    // then
    assertThat(getTrackedTakenSequenceFlowCount(FLOW_SCOPE_KEY)).isZero();
    assertThat(elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY)).isZero();
  }

  @Test
  void shouldTrackTakenSequenceFlowCountPerGateway() {
    // given
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-1");
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-1");
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-2");
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-2", "flow-3");

    // then
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                FLOW_SCOPE_KEY, wrapString("gateway-1")))
        .isEqualTo(2);
    assertThat(
            elementInstanceState.getNumberOfTakenSequenceFlows(
                FLOW_SCOPE_KEY, wrapString("gateway-2")))
        .isEqualTo(1);
  }

  @Test
  void shouldUpdateTrackedTakenSequenceFlowKeysWhenDecrementingGateway() {
    // given
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-1");
    incrementTakenSequenceFlow(FLOW_SCOPE_KEY, "gateway-1", "flow-2");
    incrementTakenSequenceFlow(OTHER_FLOW_SCOPE_KEY, "gateway-2", "flow-3");

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, wrapString("gateway-1"));

    // then
    assertThat(getTrackedTakenSequenceFlowCount(FLOW_SCOPE_KEY)).isZero();
    assertThat(getTrackedTakenSequenceFlowCount(OTHER_FLOW_SCOPE_KEY)).isEqualTo(1);
    assertThat(getTrackedTakenSequenceFlowKeys(OTHER_FLOW_SCOPE_KEY))
        .containsExactly("gateway-2->flow-3");
  }

  private void incrementTakenSequenceFlow(
      final long flowScopeKey, final String gatewayElementId, final String sequenceFlowElementId) {
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        flowScopeKey, wrapString(gatewayElementId), wrapString(sequenceFlowElementId));
  }

  private int getTrackedTakenSequenceFlowCount(final long flowScopeKey) {
    this.flowScopeKey.wrapLong(flowScopeKey);

    final var trackedTakenSequenceFlowKeys =
        takenSequenceFlowKeysByScopeKeyColumnFamily.get(
            this.flowScopeKey, PersistedTakenSequenceFlowKeys::new);

    if (trackedTakenSequenceFlowKeys == null) {
      return 0;
    }

    return trackedTakenSequenceFlowKeys.getCount();
  }

  private List<String> getTrackedTakenSequenceFlowKeys(final long flowScopeKey) {
    this.flowScopeKey.wrapLong(flowScopeKey);

    final var trackedTakenSequenceFlowKeys =
        takenSequenceFlowKeysByScopeKeyColumnFamily.get(
            this.flowScopeKey, PersistedTakenSequenceFlowKeys::new);

    if (trackedTakenSequenceFlowKeys == null) {
      return List.of();
    }

    return trackedTakenSequenceFlowKeys.getTakenSequenceFlowKeys().stream()
        .map(
            takenSequenceFlowKey ->
                "%s->%s"
                    .formatted(
                        bufferAsString(takenSequenceFlowKey.getGatewayElementId()),
                        bufferAsString(takenSequenceFlowKey.getSequenceFlowElementId())))
        .toList();
  }
}
