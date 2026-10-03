/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static io.camunda.zeebe.broker.exporter.stream.VariableRedaction.DEFAULT_SENSITIVE_VARIABLE_PATTERN;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.regex.Pattern;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class VariableRedactionTest {

  @Test
  void shouldReplaceValueOfVariableMatchingDefaultPattern() {
    // given
    final var record = variable("sensitive_ssn", "\"123-45-6789\"");

    // when
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
    assertThat(record.getName()).isEqualTo("sensitive_ssn");
  }

  @Test
  void shouldKeepValueOfVariableNotMatchingPattern() {
    // given
    final var record = variable("customerId", "\"C-42\"");

    // when
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"C-42\"");
  }

  @Test
  void shouldMatchAgainstAConfiguredPatternRatherThanAHardcodedPrefix() {
    // given -- camunda.data.protection.pattern lets an operator declare sensitivity by any
    // regular expression, e.g. a suffix instead of the default prefix
    final var pattern = Pattern.compile(".*_confidential");
    final var record = variable("ssn_confidential", "\"123-45-6789\"");

    // when
    VariableRedaction.apply(pattern, ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
  }

  @Test
  void shouldNotRedactVariablesOutsideAConfiguredPattern() {
    // given -- a name that matched the default prefix must not match under a different pattern
    final var pattern = Pattern.compile(".*_confidential");
    final var record = variable("sensitive_ssn", "\"123-45-6789\"");

    // when
    VariableRedaction.apply(pattern, ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"123-45-6789\"");
  }

  @Test
  void shouldRedactRegardlessOfTheValueType() {
    // given -- the marker replaces an object value, not only a string
    final var record = variable("sensitive_payload", "{\"pan\":\"4111111111111111\"}");

    // when
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.VARIABLE, record);

    // then
    assertThat(record.getValue()).isEqualTo("\"[REDACTED]\"");
  }

  @Test
  void shouldBeIdempotent() {
    // given -- export() is retried on failure; a second pass must not corrupt the marker
    final var record = variable("sensitive_ssn", "\"123-45-6789\"");

    // when
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.VARIABLE, record);
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.VARIABLE, record);

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
    VariableRedaction.apply(DEFAULT_SENSITIVE_VARIABLE_PATTERN, ValueType.JOB, record);

    // then
    assertThat(record.getVariables()).containsEntry("sensitive_ssn", "x");
  }

  private static VariableRecord variable(final String name, final String json) {
    return new VariableRecord()
        .setName(wrapString(name))
        .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json)));
  }
}
