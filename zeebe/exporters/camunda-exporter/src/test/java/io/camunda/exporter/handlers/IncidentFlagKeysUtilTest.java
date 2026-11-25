/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableIncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class IncidentFlagKeysUtilTest {

  private static final long P1 = 1L;
  private static final long P1E1 = 2L;
  private static final long P2 = 3L;
  private static final long P2E1 = 4L;
  private static final long SUB = 5L;
  private static final long CA = 6L;
  private static final long SUB2 = 7L;
  private static final long TASK = 8L;

  private final ProtocolFactory factory = new ProtocolFactory();

  @Test
  void shouldReturnProcessInstanceFlagKeys() {
    // given
    final var value = valueWithFlagKeys(List.of(P1, P1E1, P2, P2E1));

    // when
    final var result = IncidentFlagKeysUtil.processInstanceFlagKeys(value);

    // then
    assertThat(result).containsExactly(P1, P2);
  }

  @Test
  void shouldReturnFlowNodeFlagKeys() {
    // given
    final var value = valueWithFlagKeys(List.of(P1, P1E1, P2, P2E1));

    // when
    final var result = IncidentFlagKeysUtil.flowNodeFlagKeys(value);

    // then
    assertThat(result).containsExactly(P1E1, P2E1);
  }

  @Test
  void shouldSkipContainerElementsOnThePath() {
    // given
    final var value =
        valueWithPath(
            List.of(List.of(P1, SUB, CA), List.of(P2, SUB2, TASK)),
            List.of(P1, SUB, CA, P2, SUB2, TASK));

    // when - then
    assertThat(IncidentFlagKeysUtil.flowNodeFlagKeys(value)).containsExactly(CA, TASK);
    assertThat(IncidentFlagKeysUtil.processInstanceFlagKeys(value)).containsExactly(P1, P2);
  }

  @Test
  void shouldIgnoreFlagKeysThatAreNotOnThePath() {
    // given
    final var value = valueWithFlagKeys(List.of(99L, P1E1));

    // when - then
    assertThat(IncidentFlagKeysUtil.flowNodeFlagKeys(value)).containsExactly(P1E1);
    assertThat(IncidentFlagKeysUtil.processInstanceFlagKeys(value)).isEmpty();
  }

  @Test
  void shouldNotReturnFlowNodeKeysForProcessLevelIncident() {
    // given
    final var value = valueWithPath(List.of(List.of(P1)), List.of(P1));

    // when - then
    assertThat(IncidentFlagKeysUtil.flowNodeFlagKeys(value)).isEmpty();
    assertThat(IncidentFlagKeysUtil.processInstanceFlagKeys(value)).containsExactly(P1);
  }

  @Test
  void shouldSkipEmptyPathLists() {
    // given
    final var value = valueWithPath(List.of(List.of(), List.of(P1, P1E1)), List.of(P1, P1E1));

    // when - then
    assertThat(IncidentFlagKeysUtil.flowNodeFlagKeys(value)).containsExactly(P1E1);
    assertThat(IncidentFlagKeysUtil.processInstanceFlagKeys(value)).containsExactly(P1);
  }

  @Test
  void shouldReturnEmptyListsWhenThereAreNoFlagKeys() {
    // given
    final var value = valueWithFlagKeys(List.of());

    // when - then
    assertThat(IncidentFlagKeysUtil.processInstanceFlagKeys(value)).isEmpty();
    assertThat(IncidentFlagKeysUtil.flowNodeFlagKeys(value)).isEmpty();
  }

  @Test
  void shouldReturnProcessInstanceKeyOfElementInstanceKey() {
    // given
    final var value = valueWithFlagKeys(List.of(P1, P1E1, P2, P2E1));

    // when - then
    assertThat(IncidentFlagKeysUtil.processInstanceKeyOf(value, P1E1)).isEqualTo(P1);
    assertThat(IncidentFlagKeysUtil.processInstanceKeyOf(value, P2E1)).isEqualTo(P2);
    assertThat(IncidentFlagKeysUtil.processInstanceKeyOf(value, P2)).isEqualTo(P2);
  }

  @Test
  void shouldThrowWhenElementInstanceKeyIsNotInPath() {
    // given
    final var value = valueWithFlagKeys(List.of(P1, P1E1, P2, P2E1));

    // when - then
    assertThatThrownBy(() -> IncidentFlagKeysUtil.processInstanceKeyOf(value, 99L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("99");
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentIntent.class,
      names = {"CREATED", "RESOLVED"})
  void shouldTreatCreatedAndResolvedAsFlagIntent(final IncidentIntent intent) {
    // given
    final Record<IncidentRecordValue> incidentRecord = recordWithIntent(intent);

    // when - then
    assertThat(IncidentFlagKeysUtil.isFlagIntent(incidentRecord)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentIntent.class,
      names = {"CREATED", "RESOLVED"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldNotTreatOtherIntentsAsFlagIntent(final IncidentIntent intent) {
    // given
    final Record<IncidentRecordValue> incidentRecord = recordWithIntent(intent);

    // when - then
    assertThat(IncidentFlagKeysUtil.isFlagIntent(incidentRecord)).isFalse();
  }

  @Test
  void shouldSetFlagForCreatedAndClearItForResolved() {
    // when - then
    assertThat(IncidentFlagKeysUtil.flagValue(recordWithIntent(IncidentIntent.CREATED))).isTrue();
    assertThat(IncidentFlagKeysUtil.flagValue(recordWithIntent(IncidentIntent.RESOLVED))).isFalse();
  }

  private Record<IncidentRecordValue> recordWithIntent(final IncidentIntent intent) {
    return factory.generateRecord(ValueType.INCIDENT, r -> r.withIntent(intent));
  }

  private IncidentRecordValue valueWithFlagKeys(final List<Long> flagKeys) {
    return valueWithPath(List.of(List.of(P1, P1E1), List.of(P2, P2E1)), flagKeys);
  }

  private IncidentRecordValue valueWithPath(
      final List<List<Long>> path, final List<Long> flagKeys) {
    return ImmutableIncidentRecordValue.builder()
        .from(factory.generateObject(IncidentRecordValue.class))
        .withElementInstancePath(path)
        .withIncidentFlagKeys(flagKeys)
        .build();
  }
}
