/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.db.rdbms.write.domain.ClusterVariableDbModel;
import io.camunda.db.rdbms.write.service.ClusterVariableWriter;
import io.camunda.search.entities.ClusterVariableEntity.MetadataEntry;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.value.ClusterVariableScope;
import io.camunda.zeebe.protocol.record.value.ImmutableClusterVariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClusterVariableExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private ClusterVariableWriter writer;
  @Captor private ArgumentCaptor<ClusterVariableDbModel> modelCaptor;

  private ClusterVariableExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ClusterVariableExportHandler(writer);
  }

  @Test
  void shouldMapMetadataStringAndNumericValues() {
    // given
    final var recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .withName("myVariable")
            .withValue("myValue")
            .withScope(ClusterVariableScope.GLOBAL)
            .withMetadata(Map.of("kind", "CREDENTIAL", "schemaVersion", 2L))
            .build();
    final Record<io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    final var metadata = modelCaptor.getValue().metadata();
    assertThat(metadata).hasSize(2);
    assertThat(metadata)
        .filteredOn(entry -> entry.key().equals("kind"))
        .containsExactly(new MetadataEntry("kind", "CREDENTIAL", null));
    assertThat(metadata)
        .filteredOn(entry -> entry.key().equals("schemaVersion"))
        .containsExactly(new MetadataEntry("schemaVersion", "2", 2.0));
  }

  @Test
  void shouldMapEmptyMetadataToEmptyList() {
    // given
    final var recordValue =
        ImmutableClusterVariableRecordValue.builder()
            .withName("myVariable")
            .withValue("myValue")
            .withScope(ClusterVariableScope.GLOBAL)
            .withMetadata(Map.of())
            .build();
    final Record<io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue> record =
        factory.generateRecord(
            ValueType.CLUSTER_VARIABLE,
            r -> r.withIntent(ClusterVariableIntent.CREATED).withValue(recordValue));

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().metadata()).isEmpty();
  }
}
