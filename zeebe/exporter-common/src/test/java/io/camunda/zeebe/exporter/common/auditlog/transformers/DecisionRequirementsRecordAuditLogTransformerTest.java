/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRequirementsRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class DecisionRequirementsRecordAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final DecisionRequirementsRecordAuditLogTransformer transformer =
      new DecisionRequirementsRecordAuditLogTransformer();

  @Test
  void shouldTransformDecisionRequirementsCreatedRecord() {
    // given
    final DecisionRequirementsRecordValue recordValue =
        ImmutableDecisionRequirementsRecordValue.builder()
            .from(factory.generateObject(DecisionRequirementsRecordValue.class))
            .withResourceName("resourceName")
            .withDecisionRequirementsKey(123L)
            .withDecisionRequirementsId("decision-requirements-1")
            .withTenantId("tenant-1")
            .withDeploymentKey(1234L)
            .build();

    final Record<DecisionRequirementsRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_REQUIREMENTS,
            r -> r.withIntent(DecisionRequirementsIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("123");
    assertThat(entity.getDecisionRequirementsKey()).isEqualTo(123L);
    assertThat(entity.getDecisionRequirementsId()).isEqualTo("decision-requirements-1");
    assertThat(entity.getDeploymentKey()).isEqualTo(1234L);
    assertThat(entity.getEntityDescription()).isEqualTo("resourceName");
  }

  @Test
  void shouldTransformDecisionRequirementsDeletedRecord() {
    // given
    final DecisionRequirementsRecordValue recordValue =
        ImmutableDecisionRequirementsRecordValue.builder()
            .from(factory.generateObject(DecisionRequirementsRecordValue.class))
            .withResourceName("resourceName")
            .withDecisionRequirementsKey(456L)
            .withDecisionRequirementsId("decision-requirements-2")
            .withTenantId("tenant-2")
            .withDeploymentKey(1234L)
            .build();

    final Record<DecisionRequirementsRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION_REQUIREMENTS,
            r -> r.withIntent(DecisionRequirementsIntent.DELETED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("456");
    assertThat(entity.getDecisionRequirementsKey()).isEqualTo(456L);
    assertThat(entity.getDecisionRequirementsId()).isEqualTo("decision-requirements-2");
    assertThat(entity.getDeploymentKey()).isEqualTo(1234L);
    assertThat(entity.getEntityDescription()).isEqualTo("resourceName");
  }
}
