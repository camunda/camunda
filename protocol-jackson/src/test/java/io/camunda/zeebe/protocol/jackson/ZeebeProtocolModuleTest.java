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
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableProcess;
import java.io.IOException;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

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

  @Test
  void shouldDeserializeRecord() throws IOException {
    final ObjectMapper mapper = ZeebeProtocolModule.createMapper();
    final DeploymentRecordValue deployment =
        ImmutableDeploymentRecordValue.builder()
            .addProcessesMetadata(ImmutableProcess.builder().build())
            .build();
    final Record<DeploymentRecordValue> record =
        ImmutableRecord.<DeploymentRecordValue>builder()
            .withRecordType(RecordType.EVENT)
            .withIntent(DeploymentIntent.CREATED)
            .withValueType(ValueType.DEPLOYMENT)
            .withValue(deployment)
            .build();

    // when
    final byte[] serialized = mapper.writeValueAsBytes(record);
    final Record<DeploymentRecordValue> deserialized =
        mapper.readValue(serialized, new TypeReference<Record<DeploymentRecordValue>>() {});

    assertThat(deserialized).isEqualTo(record);
  }
}
