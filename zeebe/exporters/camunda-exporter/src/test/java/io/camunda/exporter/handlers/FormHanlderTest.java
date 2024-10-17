/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.tasklist.FormEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Form;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;

public class FormHanlderTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-form";
  private final FormHandler underTest = new FormHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.FORM);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FormEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<Form> formCreatedRecord =
        factory.generateRecord(ValueType.FORM, r -> r.withIntent(FormIntent.CREATED));

    final Record<Form> formDeletedRecord =
        factory.generateRecord(ValueType.FORM, r -> r.withIntent(FormIntent.DELETED));

    // when - then
    assertThat(underTest.handlesRecord(formCreatedRecord)).isTrue();
    assertThat(underTest.handlesRecord(formCreatedRecord)).isTrue();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;

    final Record<Form> decisionRecord =
        factory.generateRecord(
            ValueType.FORM, r -> r.withIntent(FormIntent.CREATED).withKey(expectedId));

    // when
    final var idList = underTest.generateIds(decisionRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var result = underTest.createNewEntity("id");

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final FormEntity inputEntity = new FormEntity().setId("111");
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  @Test
  void shouldUpdateEntityFromRecord() {
    // given
    /*final DecisionRecordValue decisionRecordValue =
    ImmutableDecisionRecordValue.builder()
        .from(factory.generateObject(DecisionRecordValue.class))
        .withDecisionKey(123)
        .withDecisionName("decisionName")
        .withVersion(2)
        .withDecisionId("decisionId")
        .withDecisionRequirementsId("decisionRequirementsId")
        .withDecisionRequirementsKey(222)
        .withTenantId("tenantId")
        .build();*/

    final Record<Form> decisionRecord =
        factory.generateRecord(
            ValueType.DECISION, r -> r.withIntent(DecisionIntent.CREATED) /*.withValue()*/);

    // when
    final FormEntity formEntity = new FormEntity();
    underTest.updateEntity(decisionRecord, formEntity);

    // then
    assertThat(formEntity.getId()).isEqualTo("123");
    assertThat(formEntity.getKey()).isEqualTo(123L);
    assertThat(formEntity.getVersion()).isEqualTo(2);
    assertThat(formEntity.getTenantId()).isEqualTo("tenantId");
  }
}
