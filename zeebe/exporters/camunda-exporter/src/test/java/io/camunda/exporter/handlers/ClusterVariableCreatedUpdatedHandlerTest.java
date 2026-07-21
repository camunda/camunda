/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableEntity.MetadataEntry;
import io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.ImmutableClusterVariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class ClusterVariableCreatedUpdatedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "clusterVariable";
  private final int variableSizeThreshold = 10;
  private final ClusterVariableCreatedUpdatedHandler underTest =
      new ClusterVariableCreatedUpdatedHandler(indexName, variableSizeThreshold);

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
      names = {"CREATE", "DELETE", "UPDATE", "DELETED"},
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
      names = {"CREATED", "UPDATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final ClusterVariableIntent intent) {
    // given
    final Record<ClusterVariableRecordValue> clusterVariableRecordValue =
        factory.generateRecord(ValueType.CLUSTER_VARIABLE, r -> r.withIntent(intent));

    // when - then
    assertThat(underTest.handlesRecord(clusterVariableRecordValue)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIdsForGlobalClusterVariable(final ClusterVariableIntent intent) {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .build();

    final Record<ClusterVariableRecordValue> clusterVariableRecordValueRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

    // when
    final var idList = underTest.generateIds(clusterVariableRecordValueRecord);

    // then
    assertThat(idList).containsExactly(clusterVariableRecordValue.getName() + "-GLOBAL");
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldGenerateIdsForTenantClusterVariable(final ClusterVariableIntent intent) {
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
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

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
  void shouldAddEntityOnFlush() {
    // given
    final ClusterVariableEntity inputEntity =
        new ClusterVariableEntity()
            .setId("id")
            .setName("key")
            .setValue("value")
            .setTenantId("tenantId")
            .setScope(
                io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope.TENANT)
            .setKind(ClusterVariableKind.SECRET_REFERENCE);
    final BatchRequest mockRequest = mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(eq(indexName), eq(inputEntity));
    verify(mockRequest, never()).upsert(anyString(), anyString(), any(), anyMap());
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldUpdateGlobalClusterEntityFromRecord(final ClusterVariableIntent intent) {
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
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

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

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldUpdateTenantClusterEntityFromRecord(final ClusterVariableIntent intent) {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold))
            .withScope(ClusterVariableScope.TENANT)
            .withTenantId("tenantId")
            .withMetadata(Map.of())
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

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
    assertThat(clusterVariableEntity.getMetadata()).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldUpdateEntityFromRecordWithFullValue(final ClusterVariableIntent intent) {

    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v".repeat(variableSizeThreshold + 1))
            .withTenantId("tenantId")
            .withMetadata(Map.of("kind", "CREDENTIALS"))
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

    // when
    final ClusterVariableEntity clusterVariableEntity = new ClusterVariableEntity();
    underTest.updateEntity(variableRecord, clusterVariableEntity);

    // then
    assertThat(clusterVariableEntity.getValue()).isEqualTo("v".repeat(variableSizeThreshold));
    assertThat(clusterVariableEntity.getFullValue())
        .isEqualTo("v".repeat(variableSizeThreshold + 1));
    assertThat(clusterVariableEntity.getIsPreview()).isTrue();
    assertThat(clusterVariableEntity.getMetadata())
        .containsExactly(new MetadataEntry("kind", "CREDENTIALS", null));
  }

  @Test
  void shouldSetKindOnEntityForCreatedRecord() {
    // given
    final ClusterVariableRecordValue recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .withKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE)
            .build();

    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.CREATED).withValue(recordValue));

    // when
    final ClusterVariableEntity entity = new ClusterVariableEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getKind())
        .isEqualTo(
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind
                .SECRET_REFERENCE);
  }

  @Test
  void shouldSetKindFromRecordOnUpdatedRecord() {
    // given — UPDATED records carry the authoritative kind from the engine
    final ClusterVariableRecordValue recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .withKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.SECRET_REFERENCE)
            .build();

    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.UPDATED).withValue(recordValue));

    // when
    final ClusterVariableEntity entity = new ClusterVariableEntity();
    underTest.updateEntity(record, entity);

    // then — kind is always set from the record, engine enforces immutability upstream
    assertThat(entity.getKind())
        .isEqualTo(
            io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind
                .SECRET_REFERENCE);
  }

  @Test
  void shouldDefaultKindToJsonOnCreatedWhenRecordKindIsJson() {
    // given
    final ClusterVariableRecordValue recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .withKind(io.camunda.zeebe.protocol.record.value.ClusterVariableKind.JSON)
            .build();

    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.CREATED).withValue(recordValue));

    // when
    final ClusterVariableEntity entity = new ClusterVariableEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getKind())
        .isEqualTo(io.camunda.webapps.schema.entities.clustervariable.ClusterVariableKind.JSON);
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"CREATED", "UPDATED"},
      mode = Mode.INCLUDE)
  void shouldMapMetadataWithNumericAndStringValues(final ClusterVariableIntent intent) {
    // given
    final ClusterVariableRecordValue clusterVariableRecordValue =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withValue("v")
            .withScope(ClusterVariableScope.GLOBAL)
            .withMetadata(
                Map.ofEntries(
                    Map.entry("kind", "test"),
                    Map.entry("schemaVersion", 10),
                    Map.entry("long", 10L),
                    Map.entry("double", 1.2)))
            .build();

    final Record<ClusterVariableRecordValue> variableRecord =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(intent).withValue(clusterVariableRecordValue));

    // when
    final ClusterVariableEntity entity = new ClusterVariableEntity();
    underTest.updateEntity(variableRecord, entity);

    // then
    assertThat(entity.getMetadata())
        .containsExactlyInAnyOrder(
            new MetadataEntry("kind", "test", null),
            new MetadataEntry("schemaVersion", "10", 10.0),
            new MetadataEntry("long", "10", 10.0),
            new MetadataEntry("double", "1.2", 1.2));
  }
}
