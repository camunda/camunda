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
import io.camunda.webapps.schema.descriptors.operate.index.DecisionRequirementsIndex;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionRequirementsEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ImmutableDecisionRequirementsRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

final class DecisionRequirementsHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = DecisionRequirementsIndex.INDEX_NAME;

  private final DecisionRequirementsHandler underTest = new DecisionRequirementsHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION_REQUIREMENTS);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(DecisionRequirementsEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    // given
    final Record<DecisionRequirementsRecordValue> decisionRequirementsRecord =
        generateRecord(DecisionRequirementsIntent.CREATED);

    // when - then
    assertThat(underTest.handlesRecord(decisionRequirementsRecord)).isTrue();
  }

  @Test
  void shouldNotHandleRecord() {
    Arrays.stream(DecisionRequirementsIntent.values())
        .filter(intent -> intent != DecisionRequirementsIntent.CREATED)
        .map(this::generateRecord)
        .forEach(this::assertShouldNotHandleRecordWithIntent);
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long expectedId = 123;
    final DecisionRequirementsRecordValue decisionRequirementsRecordValue =
        ImmutableDecisionRequirementsRecordValue.builder()
            .from(factory.generateObject(DecisionRecordValue.class))
            .withDecisionRequirementsKey(expectedId)
            .build();

    final Record<DecisionRequirementsRecordValue> decisionRequirementsRecord =
        factory.generateRecord(
            ValueType.DECISION,
            r ->
                r.withIntent(DecisionRequirementsIntent.CREATED)
                    .withValue(decisionRequirementsRecordValue));

    // when
    final var idList = underTest.generateIds(decisionRequirementsRecord);

    // then
    assertThat(idList).containsExactly(String.valueOf(expectedId));
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final long expectedId = 123;
    final var result = underTest.createNewEntity(String.valueOf(expectedId));

    // then
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo(String.valueOf(expectedId));
  }

  @Test
  void shouldUpdateEntity() {
    // given
    final long expectedId = 123;
    final DecisionRequirementsRecordValue recordValue =
        ImmutableDecisionRequirementsRecordValue.builder()
            .from(factory.generateObject(DecisionRequirementsRecordValue.class))
            .withDecisionRequirementsKey(expectedId)
            .withDecisionRequirementsName("name")
            .withDecisionRequirementsId("decisionRequirementsId")
            .withDecisionRequirementsVersion(2)
            .withResourceName("resourceName")
            .withResource("resource".getBytes(StandardCharsets.UTF_8))
            .withTenantId("tenantId")
            .build();

    final Record<DecisionRequirementsRecordValue> decisionRequirementsRecord =
        factory.generateRecord(
            ValueType.DECISION_REQUIREMENTS,
            r -> r.withIntent(DecisionRequirementsIntent.CREATED).withValue(recordValue));

    final DecisionRequirementsEntity entity = new DecisionRequirementsEntity();

    // when
    underTest.updateEntity(decisionRequirementsRecord, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(expectedId));
    assertThat(entity.getKey()).isEqualTo(expectedId);
    assertThat(entity.getName()).isEqualTo(recordValue.getDecisionRequirementsName());
    assertThat(entity.getDecisionRequirementsId())
        .isEqualTo(recordValue.getDecisionRequirementsId());
    assertThat(entity.getVersion()).isEqualTo(recordValue.getDecisionRequirementsVersion());
    assertThat(entity.getResourceName()).isEqualTo(recordValue.getResourceName());
    assertThat(entity.getXml())
        .isEqualTo(new String(recordValue.getResource(), StandardCharsets.UTF_8));
    assertThat(entity.getTenantId()).isEqualTo(recordValue.getTenantId());
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final DecisionRequirementsEntity inputEntity = new DecisionRequirementsEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }

  private Record<DecisionRequirementsRecordValue> generateRecord(
      final DecisionRequirementsIntent intent) {
    return factory.generateRecord(ValueType.DECISION_REQUIREMENTS, r -> r.withIntent(intent));
  }

  private void assertShouldNotHandleRecordWithIntent(
      final Record<DecisionRequirementsRecordValue> decisionRequirementsRecord) {

    assertThat(underTest.handlesRecord(decisionRequirementsRecord)).isFalse();
  }
}
