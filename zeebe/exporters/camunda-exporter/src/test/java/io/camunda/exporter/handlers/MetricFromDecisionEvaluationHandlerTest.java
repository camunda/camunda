/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.MetricFromDecisionEvaluationHandler.EVENT_DECISION_INSTANCE_EVALUATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.index.MetricIndex;
import io.camunda.webapps.schema.entities.MetricEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableDecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluatedDecisionValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

public class MetricFromDecisionEvaluationHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = MetricIndex.INDEX_NAME;

  private final MetricFromDecisionEvaluationHandler underTest =
      new MetricFromDecisionEvaluationHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.DECISION_EVALUATION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MetricEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    Arrays.stream(DecisionEvaluationIntent.values())
        .filter(i -> !i.name().equals(DecisionEvaluationIntent.EVALUATE.name()))
        .forEach(
            intent -> {
              // given
              final Record<DecisionEvaluationRecordValue> decisionEvaluationRecord =
                  factory.generateRecord(ValueType.DECISION_EVALUATION, r -> r.withIntent(intent));

              // when - then
              assertThat(underTest.handlesRecord(decisionEvaluationRecord)).isTrue();
            });
  }

  @Test
  void shouldNotHandleRecord() {
    // given
    final Record<DecisionEvaluationRecordValue> decisionEvaluationRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION, r -> r.withIntent(DecisionEvaluationIntent.EVALUATE));

    // when - then
    assertThat(underTest.handlesRecord(decisionEvaluationRecord)).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final long recordKey = 123L;
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .withEvaluatedDecisions(
                List.of(
                    ImmutableEvaluatedDecisionValue.builder().build(),
                    ImmutableEvaluatedDecisionValue.builder().build(),
                    ImmutableEvaluatedDecisionValue.builder().build()))
            .build();
    final Record<DecisionEvaluationRecordValue> decisionEvaluationRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withValue(recordValue)
                    .withKey(recordKey));

    // when
    final var ids = underTest.generateIds(decisionEvaluationRecord);

    // then
    assertThat(ids).isNotNull();
    assertThat(ids).containsExactlyInAnyOrder(recordKey + "-1", recordKey + "-2", recordKey + "-3");
  }

  @Test
  void shouldCreateNewEntity() {
    // when
    final var entity = underTest.createNewEntity("id");

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo("id");
  }

  @Test
  void shouldUpdateEntity() {
    // given
    final String tenantId = "tenantId";
    final long recordKey = 123L;
    final long timestamp = new Date().getTime();
    final DecisionEvaluationRecordValue recordValue =
        ImmutableDecisionEvaluationRecordValue.builder()
            .withTenantId(tenantId)
            .withEvaluatedDecisions(
                List.of(
                    ImmutableEvaluatedDecisionValue.builder().build(),
                    ImmutableEvaluatedDecisionValue.builder().build(),
                    ImmutableEvaluatedDecisionValue.builder().build()))
            .build();
    final Record<DecisionEvaluationRecordValue> decisionEvaluationRecord =
        factory.generateRecord(
            ValueType.DECISION_EVALUATION,
            r ->
                r.withIntent(DecisionEvaluationIntent.EVALUATED)
                    .withTimestamp(timestamp)
                    .withValue(recordValue)
                    .withKey(recordKey));

    final MetricEntity entity = new MetricEntity().setId(recordKey + "-1");

    // when
    underTest.updateEntity(decisionEvaluationRecord, entity);

    // then
    assertThat(entity.getId()).isNull();
    assertThat(entity.getEvent()).isEqualTo(EVENT_DECISION_INSTANCE_EVALUATED);
    assertThat(entity.getValue()).isEqualTo(recordKey + "-1");
    assertThat(entity.getEventTime())
        .isEqualTo(OffsetDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneOffset.UTC));
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final MetricEntity inputEntity = new MetricEntity();
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(indexName, inputEntity);
  }
}
