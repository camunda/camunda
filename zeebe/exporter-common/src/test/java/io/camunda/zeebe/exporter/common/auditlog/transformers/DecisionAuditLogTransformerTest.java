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
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class DecisionAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final DecisionAuditLogTransformer transformer = new DecisionAuditLogTransformer();

  @Test
  void shouldTransformDecisionRecord() {
    // given
    final DecisionRecordValue recordValue =
        ImmutableDecisionRecordValue.builder()
            .from(factory.generateObject(DecisionRecordValue.class))
            .withDeploymentKey(123L)
            .withDecisionRequirementsKey(456L)
            .withDecisionKey(789L)
            .withDecisionName("decisionName")
            .withDecisionRequirementsId("decisionRequirementsId")
            .withDecisionId("decisionId")
            .withTenantId("tenant-1")
            .build();

    final Record<DecisionRecordValue> record =
        factory.generateRecord(
            ValueType.DECISION, r -> r.withIntent(DecisionIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("789");
    assertThat(entity.getDeploymentKey()).isEqualTo(123L);
    assertThat(entity.getDecisionRequirementsKey()).isEqualTo(456L);
    assertThat(entity.getDecisionDefinitionKey()).isEqualTo(789L);
    assertThat(entity.getDecisionRequirementsId()).isEqualTo("decisionRequirementsId");
    assertThat(entity.getDecisionDefinitionId()).isEqualTo("decisionId");
    assertThat(entity.getOperationType()).isEqualTo(AuditLogOperationType.CREATE);
    assertThat(entity.getEntityDescription()).isEqualTo("decisionName");
  }
}
