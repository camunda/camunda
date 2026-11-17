/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import org.junit.jupiter.api.Test;

final class CommandDistributionRecordTest {

  @Test
  void shouldReadBackDistributedProcessDefinitionSuspensionCommand() {
    // given
    final var record =
        new CommandDistributionRecord()
            .setValueType(ValueType.PROCESS_DEFINITION_SUSPENSION)
            .setIntent(ProcessDefinitionSuspensionIntent.SUSPEND)
            .setCommandValue(new ProcessDefinitionSuspensionRecord().setProcessDefinitionKey(123L));

    // when
    final var commandValue = record.getCommandValue();

    // then
    assertThat(commandValue)
        .isInstanceOfSatisfying(
            ProcessDefinitionSuspensionRecord.class,
            suspension -> assertThat(suspension.getProcessDefinitionKey()).isEqualTo(123L));
  }
}
