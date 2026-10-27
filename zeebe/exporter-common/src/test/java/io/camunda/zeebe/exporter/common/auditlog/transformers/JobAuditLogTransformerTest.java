/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class JobAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final JobAuditLogTransformer transformer = new JobAuditLogTransformer();

  @Test
  void shouldTransformIncidentResolutionRecord() {
    // given
    final JobRecordValue recordValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("test-job")
            .withBpmnProcessId("proc-1")
            .withProcessDefinitionKey(456L)
            .withProcessInstanceKey(123L)
            .withElementInstanceKey(789L)
            .withTenantId("tenant-1")
            .build();

    final Record<JobRecordValue> record =
        factory.generateRecord(
            ValueType.JOB,
            r -> r.withKey(222L).withIntent(JobIntent.COMPLETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityDescription()).isEqualTo("test-job");
    assertThat(entity.getProcessDefinitionId()).isEqualTo("proc-1");
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(456L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(789L);
    assertThat(entity.getJobKey()).isEqualTo(222L);
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.COMPLETE);
    assertThat(entity.getRootProcessInstanceKey())
        .isPositive()
        .isEqualTo(record.getValue().getRootProcessInstanceKey());
  }
}
