/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import java.util.Set;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableRedactionTest {

  @Test
  void shouldReplaceValueOfVariableDeclaredSensitive() {
    // given -- the protection-modes declaration is decided upstream, in VariableBehavior, and
    // arrives here as metadata on the record rather than as a name to match
    final var record = variable("sensitive_ssn", "\"123-45-6789\"", true);

    // when
    VariableRedaction.apply(ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
    assertThat(record.getName()).isEqualTo("sensitive_ssn");
  }

  @Test
  void shouldKeepValueOfVariableNotDeclaredSensitive() {
    // given
    final var record = variable("customerId", "\"C-42\"", false);

    // when
    VariableRedaction.apply(ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"C-42\"");
  }

  @Test
  void shouldRedactRegardlessOfTheValueType() {
    // given -- the marker replaces an object value, not only a string
    final var record = variable("sensitive_payload", "{\"pan\":\"4111111111111111\"}", true);

    // when
    VariableRedaction.apply(ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
  }

  @Test
  void shouldBeIdempotent() {
    // given -- export() is retried on failure; a second pass must not corrupt the marker
    final var record = variable("sensitive_ssn", "\"123-45-6789\"", true);

    // when
    VariableRedaction.apply(ValueType.VARIABLE, record);
    VariableRedaction.apply(ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
  }

  @Test
  void shouldLeaveNonVariableRecordsUntouched() {
    // given -- the PoC only covers the variable record; a job payload carrying the same variable
    // is a known gap and must not be silently half-redacted
    final var record = new JobRecord();
    record.setVariables(
        new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"sensitive_ssn\":\"x\"}")));

    // when
    VariableRedaction.apply(ValueType.JOB, record);

    // then
    assertThat(record.getVariables()).containsEntry("sensitive_ssn", "x");
  }

  private static VariableRecord variable(
      final String name, final String json, final boolean sensitive) {
    return new VariableRecord()
        .setName(wrapString(name))
        .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json)))
        .setProtectionModes(sensitive ? Set.of(ProtectionMode.REDACT) : Set.of());
  }
}
