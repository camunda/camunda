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
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableForm;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

class FormAuditLogTransformerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final FormAuditLogTransformer transformer = new FormAuditLogTransformer();

  @Test
  void shouldTransformFormRecord() {
    // given
    final Form recordValue =
        ImmutableForm.builder()
            .from(factory.generateObject(Form.class))
            .withResourceName("formResource")
            .withDeploymentKey(123L)
            .withFormKey(456L)
            .withTenantId("tenant-1")
            .build();

    final Record<Form> record =
        factory.generateRecord(
            ValueType.FORM, r -> r.withIntent(FormIntent.CREATED).withValue(recordValue));

    // when
    final var entity = AuditLogEntry.of(record);
    transformer.transform(record, entity);

    // then
    assertThat(entity.getEntityKey()).isEqualTo("456");
    assertThat(entity.getDeploymentKey()).isEqualTo(123L);
    assertThat(entity.getFormKey()).isEqualTo(456L);
    assertThat(entity.getEntityDescription()).isEqualTo("formResource");
  }

  @Test
  void shouldScheduleCleanUp() {
    // given
    final Record<Form> record =
        factory.generateRecord(ValueType.FORM, r -> r.withIntent(FormIntent.DELETED));

    // then
    assertThat(transformer.triggersCleanUp(record)).isTrue();
  }
}
