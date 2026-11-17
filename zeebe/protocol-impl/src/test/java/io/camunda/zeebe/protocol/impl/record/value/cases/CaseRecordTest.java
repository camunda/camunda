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
            .addStage("review", CaseStageStatus.ACTIVE)
            .addStage("ship", CaseStageStatus.NOT_STARTED);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new CaseRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getStages())
        .extracting(CaseStageValue::getElementId, CaseStageValue::getStatus)
        .containsExactly(
            tuple("review", CaseStageStatus.ACTIVE), tuple("ship", CaseStageStatus.NOT_STARTED));
  }
}
