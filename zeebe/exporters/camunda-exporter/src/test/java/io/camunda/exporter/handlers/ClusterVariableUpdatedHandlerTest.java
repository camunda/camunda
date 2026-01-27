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
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.ImmutableClusterVariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ClusterVariableUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "clusterVariable";
  private final int variableSizeThreshold = 10;
  private final ClusterVariableUpdatedHandler underTest =
      new ClusterVariableUpdatedHandler(indexName, variableSizeThreshold);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.CLUSTER_VARIABLE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(ClusterVariableEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATE", "DELETE", "UPDATE", "CREATED", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldHandleRecord(final ClusterVariableIntent intent) {
    // given
    final Record<ClusterVariableRecordValue> clusterVariableRecordValue =
        factory.generateRecord(ValueType.CLUSTER_VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(clusterVariableRecordValue)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"UPDATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final ClusterVariableIntent intent) {
    // given
    final Record<ClusterVariableRecordValue> clusterVariableRecordValue =
        factory.generateRecord(ValueType.CLUSTER_VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(clusterVariableRecordValue)).isFalse();
  }

  @Test
  void shouldGenerateIdsForGlobalClusterVariable() {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .build();

    final Record<ClusterVariableRecordValue> clusterVariableRecordValueRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(clusterVariableRecordValue));

    // when
    final var idList = underTest.generateIds(clusterVariableRecordValueRecord);

    // then
    assertThat(idList).containsExactly(clusterVariableRecordValue.getName() + "-GLOBAL");
  }

  @Test
  void shouldGenerateIdsForTenantClusterVariable() {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.TENANT)
            .withTenantId("tenantId")
            .build();

    final Record<ClusterVariableRecordValue> clusterVariableRecordValueRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(clusterVariableRecordValue));

    // when
    final var idList = underTest.generateIds(clusterVariableRecordValueRecord);

    // then
    assertThat(idList)
        .containsExactly(
            clusterVariableRecordValue.getName()
                + "-"
                + clusterVariableRecordValue.getTenantId()
                + "-TENANT");
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
  void shouldUpsertEntityOnFlush() {
    // given
    final ClusterVariableEntity inputEntity =
        new ClusterVariableEntity()
            .setId("id")
            .setName("key")
            .setValue("value")
            .setTenantId("tenantId")
            .setScope(
                io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope.TENANT);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(indexName, inputEntity.getId(), inputEntity, null);
  }

  @Test
  void shouldUpdateGlobalClusterEntityFromRecord() {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold))
            .withScope(ClusterVariableScope.GLOBAL)
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(clusterVariableRecordValue));

    // when
    final ClusterVariableEntity clusterVariableEntity = new ClusterVariableEntity();
    underTest.updateEntity(variableRecord, clusterVariableEntity);

    // then
    assertThat(clusterVariableEntity.getName()).isEqualTo(clusterVariableRecordValue.getName());
    assertThat(clusterVariableEntity.getScope())
        .isEqualTo(
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope.fromProtocol(
                clusterVariableRecordValue.getScope()));
    assertThat(clusterVariableEntity.getValue()).isEqualTo(clusterVariableRecordValue.getValue());
    assertThat(clusterVariableEntity.getIsPreview()).isFalse();
    assertThat(clusterVariableEntity.getFullValue()).isNull();
  }

  @Test
  void shouldUpdateTenantClusterEntityFromRecord() {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold))
            .withScope(ClusterVariableScope.TENANT)
            .withTenantId("tenantId")
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(clusterVariableRecordValue));

    // when
    final ClusterVariableEntity clusterVariableEntity = new ClusterVariableEntity();
    underTest.updateEntity(variableRecord, clusterVariableEntity);

    // then
    assertThat(clusterVariableEntity.getName()).isEqualTo(clusterVariableRecordValue.getName());
    assertThat(clusterVariableEntity.getTenantId())
        .isEqualTo(clusterVariableRecordValue.getTenantId());
    assertThat(clusterVariableEntity.getScope())
        .isEqualTo(
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope.fromProtocol(
                clusterVariableRecordValue.getScope()));
    assertThat(clusterVariableEntity.getValue()).isEqualTo(clusterVariableRecordValue.getValue());
    assertThat(clusterVariableEntity.getIsPreview()).isFalse();
    assertThat(clusterVariableEntity.getFullValue()).isNull();
  }

  @Test
  void shouldUpdateEntityFromRecordWithFullValue() {

    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold + 1))
            .withTenantId("tenantId")
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(clusterVariableRecordValue));

    // when
    final ClusterVariableEntity clusterVariableEntity = new ClusterVariableEntity();
    underTest.updateEntity(variableRecord, clusterVariableEntity);

    // then
    assertThat(clusterVariableEntity.getValue()).isEqualTo("v".repeat(variableSizeThreshold));
    assertThat(clusterVariableEntity.getFullValue())
        .isEqualTo("v".repeat(variableSizeThreshold + 1));
    assertThat(clusterVariableEntity.getIsPreview()).isTrue();
  }
}
