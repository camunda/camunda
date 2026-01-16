/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.db.rdbms.write.service.MappingRuleWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class MappingRuleExportHandlerTest {
  private static final Set<MappingRuleIntent> TEST_EXPORTABLE_INTENTS =
      EnumSet.of(MappingRuleIntent.CREATED, MappingRuleIntent.DELETED, MappingRuleIntent.UPDATED);

  private final ProtocolFactory factory = new ProtocolFactory();

  @Mock private MappingRuleWriter writer;
  @Mock private HistoryCleanupService historyCleanupService;

  @Captor private ArgumentCaptor<MappingRuleDbModel> dbModelCaptor;

  private MappingRuleExportHandler handler;

  @BeforeEach
  void setUp() {
    handler = new MappingRuleExportHandler(writer, historyCleanupService);
  }

  private static Stream<MappingRuleIntent> exportableIntents() {
    return TEST_EXPORTABLE_INTENTS.stream();
  }

  @ParameterizedTest(name = "Should be able to export record with intent: {0}")
  @MethodSource("exportableIntents")
  void shouldExportRecord(final MappingRuleIntent intent) {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(ValueType.MAPPING_RULE, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should be able to export record with intent: %s", intent)
        .isTrue();
  }

  private static Stream<MappingRuleIntent> nonExportableIntents() {
    return Stream.of(MappingRuleIntent.values())
        .filter(Predicate.not(TEST_EXPORTABLE_INTENTS::contains));
  }

  @ParameterizedTest(name = "Should not export record with unsupported intent: {0}")
  @MethodSource("nonExportableIntents")
  void shouldNotExportRecord(final MappingRuleIntent intent) {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(ValueType.MAPPING_RULE, r -> r.withIntent(intent));

    // when - then
    assertThat(handler.canExport(record))
        .as("Handler should not be able to export record with unsupported intent: %s", intent)
        // If this assertion fails, it means that the given intent was recently added to
        // `MappingRuleExporterHandleTest#EXPORTABLE_INTENTS`.
        // In that case:
        // - Add it to `MappingRuleExporterHandleTest#TEST_EXPORTABLE_INTENTS` set
        // - Review whether it needs custom handling in `MappingRuleExporterHandle#export`:
        //   - If so, add a dedicated handling in `MappingRuleExporterHandler#export`
        //   - Add a dedicated test for the new intent in this class
        .isFalse();
  }

  @Test
  @DisplayName("Should handle record with DELETED intent")
  void shouldHandleDeletedMappingRuleRecord() {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(
            ValueType.MAPPING_RULE, r -> r.withIntent(MappingRuleIntent.DELETED));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(writer).delete(eq(recordValue.getMappingRuleId()));

    // ensure no other methods are called
    verifyNoMoreInteractions(writer);
  }

  @Test
  @DisplayName("Should handle record with CREATED intent")
  void shouldHandleCreatedMappingRuleRecord() {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(
            ValueType.MAPPING_RULE, r -> r.withIntent(MappingRuleIntent.CREATED));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(writer).create(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue())
        .satisfies(model -> assertMappingRuleModelEqualsRecord(model, record));

    // ensure no other methods are called
    verifyNoMoreInteractions(writer);
  }

  @Test
  @DisplayName("Should handle record with UPDATED intent")
  void shouldHandleUpdatedMappingRuleRecord() {
    // given
    final Record<MappingRuleRecordValue> record =
        factory.generateRecord(
            ValueType.MAPPING_RULE, r -> r.withIntent(MappingRuleIntent.UPDATED));
    final var recordValue = record.getValue();

    // when
    handler.export(record);

    // then
    verify(writer).update(dbModelCaptor.capture());
    assertThat(dbModelCaptor.getValue())
        .satisfies(model -> assertMappingRuleModelEqualsRecord(model, record));

    // ensure no other methods are called
    verifyNoMoreInteractions(writer);
  }

  private void assertMappingRuleModelEqualsRecord(
      final MappingRuleDbModel model, final Record<MappingRuleRecordValue> record) {
    final var recordValue = record.getValue();
    assertThat(model.mappingRuleId()).isEqualTo(recordValue.getMappingRuleId());
    assertThat(model.mappingRuleKey()).isEqualTo(recordValue.getMappingRuleKey());
    assertThat(model.name()).isEqualTo(recordValue.getName());
    assertThat(model.claimName()).isEqualTo(recordValue.getClaimName());
    assertThat(model.claimValue()).isEqualTo(recordValue.getClaimValue());
  }
}
