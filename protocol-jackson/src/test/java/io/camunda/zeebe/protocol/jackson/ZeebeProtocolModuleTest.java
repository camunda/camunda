/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.io.IOException;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@SuppressWarnings("unchecked")
@Execution(ExecutionMode.CONCURRENT)
final class ZeebeProtocolModuleTest {
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ZeebeProtocolModule());

  @Test
  void shouldDeserialize() throws IOException {
    // given
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
    final byte[] serialized = MAPPER.writeValueAsBytes(batch);
    final JobBatchRecordValue deserialized =
        MAPPER.readValue(serialized, JobBatchRecordValue.class);
    final JobBatchRecordValue other = MAPPER.readValue(serialized, JobBatchRecordValue.class);

    assertThat(deserialized).isEqualTo(other).isEqualTo(batch);
  }

  @Test
  void shouldIgnoreUnknownPropertiesOfRecord() throws IOException {
    // given
    final Record<RecordValue> record =
        ImmutableRecord.builder()
            .withValueType(ValueType.VARIABLE)
            .withValue(ImmutableVariableRecordValue.builder().build())
            .withIntent(VariableIntent.CREATED)
            .withRecordType(RecordType.EVENT)
            .build();
    final ObjectNode jsonRecord = MAPPER.valueToTree(record);

    // when
    jsonRecord.put("nonExistentProperty", "something");
    final byte[] serialized = MAPPER.writeValueAsBytes(jsonRecord);
    final Record<RecordValue> deserialized =
        (Record<RecordValue>) MAPPER.readValue(serialized, Record.class);

    // then
    assertThat(deserialized).isEqualTo(record);
  }

  @Test
  void shouldIgnoreUnknownPropertiesOfRecordValue() throws IOException {
    // given
    final VariableRecordValue value = ImmutableVariableRecordValue.builder().build();
    final ObjectNode jsonValue = MAPPER.valueToTree(value);

    // when
    jsonValue.put("nonExistentProperty", "something");
    final byte[] serialized = MAPPER.writeValueAsBytes(jsonValue);
    final VariableRecordValue deserialized =
        MAPPER.readValue(serialized, VariableRecordValue.class);

    // then
    assertThat(deserialized).isEqualTo(value);
  }
}
