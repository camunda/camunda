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

import io.camunda.db.rdbms.write.domain.VariableDbModel;
import io.camunda.db.rdbms.write.domain.VariableDbModel.ProtectionModeDbModel;
import io.camunda.db.rdbms.write.service.VariableWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableMetadataValue;
import io.camunda.zeebe.protocol.record.value.ImmutableVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VariableExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private VariableWriter writer;
  @Captor private ArgumentCaptor<VariableDbModel> modelCaptor;

  private VariableExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new VariableExportHandler(writer);
  }

  @Test
  void shouldMapProtectionModesOnCreate() {
    // given
    final Record<VariableRecordValue> record =
        variableRecord(
            VariableIntent.CREATED,
            ImmutableVariableMetadataValue.builder()
                .addProtectionModes(ProtectionMode.REDACT)
                .build());

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().protectionModes())
        .containsExactly(ProtectionModeDbModel.REDACT);
  }

  @Test
  void shouldMapProtectionModesOnUpdate() {
    // given
    final Record<VariableRecordValue> record =
        variableRecord(
            VariableIntent.UPDATED,
            ImmutableVariableMetadataValue.builder()
                .addProtectionModes(ProtectionMode.REDACT)
                .build());

    // when
    handler.export(record);

    // then
    verify(writer).update(modelCaptor.capture());
    assertThat(modelCaptor.getValue().protectionModes())
        .containsExactly(ProtectionModeDbModel.REDACT);
  }

  @Test
  void shouldMapNoProtectionModesForUnprotectedVariable() {
    // given
    final Record<VariableRecordValue> record =
        variableRecord(VariableIntent.CREATED, ImmutableVariableMetadataValue.builder().build());

    // when
    handler.export(record);

    // then
    verify(writer).create(modelCaptor.capture());
    assertThat(modelCaptor.getValue().protectionModes()).isEmpty();
  }

  private Record<VariableRecordValue> variableRecord(
      final VariableIntent intent, final ImmutableVariableMetadataValue metadata) {
    return factory.generateRecord(
        ValueType.VARIABLE,
        r ->
            r.withIntent(intent)
                .withValue(
                    ImmutableVariableRecordValue.builder()
                        .from(factory.generateObject(VariableRecordValue.class))
                        .withMetadata(metadata)
                        .build()));
  }
}
