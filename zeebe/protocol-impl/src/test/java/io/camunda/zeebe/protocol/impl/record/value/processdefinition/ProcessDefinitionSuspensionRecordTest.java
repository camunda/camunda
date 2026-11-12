/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionResolvedVersionValue;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class ProcessDefinitionSuspensionRecordTest {

  @Test
  void shouldRoundTripEveryField() {
    // given
    final var original =
        new ProcessDefinitionSuspensionRecord()
            .setProcessDefinitionKey(1L)
            .setBpmnProcessId("process-a")
            .setVersion(3)
            .setTenantId("tenant-a")
            .setCascade(true)
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(1L)
                    .setVersion(3)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED))
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(2L)
                    .setVersion(2)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED))
            .setBatchOperationKey(99L);

    // when
    final var buffer = new UnsafeBuffer(new byte[original.getLength()]);
    original.write(buffer, 0);
    final var copy = new ProcessDefinitionSuspensionRecord();
    copy.wrap(buffer);

    // then
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(1L);
    assertThat(copy.getBpmnProcessId()).isEqualTo("process-a");
    assertThat(copy.getVersion()).isEqualTo(3);
    assertThat(copy.getTenantId()).isEqualTo("tenant-a");
    assertThat(copy.isCascade()).isTrue();
    assertThat(copy.getBatchOperationKey()).isEqualTo(99L);
    assertThat(copy.getResolvedVersions())
        .extracting(
            ProcessDefinitionSuspensionResolvedVersionValue::getProcessDefinitionKey,
            ProcessDefinitionSuspensionResolvedVersionValue::getVersion,
            ProcessDefinitionSuspensionResolvedVersionValue::getOutcome)
        .containsExactly(
            tuple(1L, 3, ProcessDefinitionSuspensionOutcome.SUSPENDED),
            tuple(2L, 2, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED));
  }

  @Test
  void shouldDefaultMissingFields() {
    // given
    final var buffer = new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}"));

    // when
    final var record = new ProcessDefinitionSuspensionRecord();
    record.wrap(buffer);

    // then
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getBpmnProcessId()).isEmpty();
    assertThat(record.getVersion()).isEqualTo(-1);
    assertThat(record.getTenantId()).isEqualTo("<default>");
    assertThat(record.isCascade()).isFalse();
    assertThat(record.getResolvedVersions()).isEmpty();
    assertThat(record.getBatchOperationKey()).isEqualTo(-1L);
  }
}
