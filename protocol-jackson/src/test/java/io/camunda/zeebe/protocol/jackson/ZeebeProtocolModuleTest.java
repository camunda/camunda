/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.record.ProtocolRecordFactory;
import java.io.IOException;
import java.util.stream.Stream;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Execution(ExecutionMode.CONCURRENT)
final class ZeebeProtocolModuleTest {
  @Test
  void shouldDeserialize() throws IOException {
    final ObjectMapper mapper = ZeebeProtocolModule.createMapper();
    final JobRecordValue value =
        ImmutableJobRecordValue.builder()
            .withBpmnProcessId("bpmnProcessId")
            .withVariables(Maps.newHashMap("foo", "bar"))
            .build();
    final JobBatchRecordValue batch =
        ImmutableJobBatchRecordValue.builder()
            .addJob(value)
            .addJobKey(1L)
            .withWorker("worker")
            .build();

    // when
    final byte[] serialized = mapper.writeValueAsBytes(batch);
    final JobBatchRecordValue deserialized =
        mapper.readValue(serialized, JobBatchRecordValue.class);
    final JobBatchRecordValue other = mapper.readValue(serialized, JobBatchRecordValue.class);

    assertThat(deserialized).isEqualTo(other).isEqualTo(batch);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("recordProvider")
  void shouldDeserializeRecord(
      @SuppressWarnings("unused") final String testName, final Record<?> record)
      throws IOException {
    // given
    final ObjectMapper mapper = ZeebeProtocolModule.createMapper();

    // when
    final byte[] serialized = mapper.writeValueAsBytes(record);
    final Record<?> deserialized = mapper.readValue(serialized, new TypeReference<Record<?>>() {});

    // then
    assertThat(deserialized).isEqualTo(record);
  }

  private static Stream<Arguments> recordProvider() {
    final ProtocolRecordFactory factory = new ProtocolRecordFactory();
    return factory.generateForAllValueTypes().map(r -> Arguments.of(r.getValueType().name(), r));
  }
}
