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

public class ClusterVariableDeletedHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "clusterVariable";
  private final ClusterVariableDeletedHandler underTest =
      new ClusterVariableDeletedHandler(indexName);

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
      names = {"DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final ClusterVariableIntent intent) {
    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(ValueType.CLUSTER_VARIABLE, r -> r.withIntent(intent));
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ClusterVariableIntent.class,
      names = {"DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final ClusterVariableIntent intent) {
    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(ValueType.CLUSTER_VARIABLE, r -> r.withIntent(intent));
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateIdsForGlobalClusterVariable() {
    final ClusterVariableRecordValue value =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.GLOBAL)
            .build();
    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.DELETED).withValue(value));
    final var idList = underTest.generateIds(record);
    assertThat(idList).containsExactly(value.getName() + "-GLOBAL");
  }

  @Test
  void shouldGenerateIdsForTenantClusterVariable() {
    final ClusterVariableRecordValue value =
        ImmutableClusterVariableRecordValue.builder()
            .from(factory.generateObject(ClusterVariableRecordValue.class))
            .withScope(ClusterVariableScope.TENANT)
            .withTenantId("tenantId")
            .build();
    final Record<ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.DELETED).withValue(value));
    final var idList = underTest.generateIds(record);
    assertThat(idList).containsExactly(value.getName() + "-" + value.getTenantId() + "-TENANT");
  }

  @Test
  void shouldCreateNewEntity() {
    final var result = underTest.createNewEntity("id");
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  void shouldDeleteEntityOnFlush() {
    final ClusterVariableEntity inputEntity =
        new ClusterVariableEntity()
            .setId("id")
            .setName("key")
            .setValue("value")
            .setTenantId("tenantId")
            .setScope(
                io.camunda.webapps.schema.entities.clustervariable.ClusterVariableScope.TENANT);
    final BatchRequest mockRequest = mock(BatchRequest.class);
    underTest.flush(inputEntity, mockRequest);
    verify(mockRequest, times(1)).delete(indexName, inputEntity.getId());
  }
}
