/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue.CaseStageValue;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class CaseRecordTest {

  @Test
  void shouldRoundTripStagesOverMsgPack() {
    // given
    final var original =
        new CaseRecord()
            .setProcessInstanceKey(123L)
            .setProcessDefinitionKey(456L)
            .setBpmnProcessId("order-process")
            .addStage("review", "Review", CaseStageStatus.ACTIVE)
            .addStage("ship", "Ship", CaseStageStatus.NOT_STARTED);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new CaseRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getStages())
        .extracting(
            CaseStageValue::getElementId, CaseStageValue::getName, CaseStageValue::getStatus)
        .containsExactly(
            tuple("review", "Review", CaseStageStatus.ACTIVE),
            tuple("ship", "Ship", CaseStageStatus.NOT_STARTED));
  }

  @Test
  void shouldDefaultStateToActive() {
    // when
    final var record = new CaseRecord();

    // then
    assertThat(record.getState()).isEqualTo(CaseLifecycleState.ACTIVE);
    assertThat(record.getCaseKey()).isEqualTo(-1L);
  }

  @Test
  void shouldCarryStageName() {
    // given
    final var record = new CaseRecord();

    // when
    record.addStage("stage-a", "Stage A", CaseStageStatus.NOT_STARTED);

    // then
    assertThat(record.getStages())
        .singleElement()
        .satisfies(
            stage -> {
              assertThat(stage.getElementId()).isEqualTo("stage-a");
              assertThat(stage.getName()).isEqualTo("Stage A");
              assertThat(stage.getStatus()).isEqualTo(CaseStageStatus.NOT_STARTED);
            });
  }

  @Test
  void shouldDefaultStageNameToEmpty() {
    // when
    final var stage = new CaseStageRecord().setElementId("stage-a");

    // then
    assertThat(stage.getName()).isEmpty();
  }
}
