/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context.RecordFilter;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ImmutableRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class ElasticsearchExporterTest {

  private final ElasticsearchExporterConfiguration config =
      new ElasticsearchExporterConfiguration();
  private final ExporterTestContext context =
      new ExporterTestContext().setConfiguration(new ExporterTestConfiguration<>("test", config));
  private final ExporterTestController controller = new ExporterTestController();
  private final ElasticClient client = mock(ElasticClient.class);
  private final ElasticsearchExporter exporter =
      new ElasticsearchExporter() {
        @Override
        protected ElasticClient createClient() {
          return client;
        }
      };

  @BeforeEach
  void beforeEach() {
    when(client.putIndexTemplate(any())).thenReturn(true);
    when(client.putComponentTemplate()).thenReturn(true);
  }

  @Test
  void shouldNotFailOnOpenIfElasticIsUnreachable() {
    // given
    final var exporter = new ElasticsearchExporter();
    exporter.configure(context);

    // when
    exporter.open(controller);

    // then
    assertThatThrownBy(() -> exporter.export(mock(Record.class)))
        .isInstanceOf(ElasticsearchExporterException.class);
  }

  @Nested
  final class RecordFilterTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldRejectDisabledValueType(final ValueType valueType) {
      // given
      TestSupport.setIndexingForValueType(config.index, valueType, false);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptValue(valueType))
          .as("record filter should reject value type based on configuration")
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldAcceptEnabledValueType(final ValueType valueType) {
      // given
      TestSupport.setIndexingForValueType(config.index, valueType, true);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptValue(valueType))
          .as("record filter should accept value type based on configuration")
          .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = RecordType.class,
        names = {"NULL_VAL", "SBE_UNKNOWN"},
        mode = Mode.EXCLUDE)
    void shouldRejectDisabledRecordType(final RecordType recordType) {
      // given
      TestSupport.setIndexingForRecordType(config.index, recordType, false);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptType(recordType))
          .as("record filter should reject record type based on configuration")
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = RecordType.class,
        names = {"NULL_VAL", "SBE_UNKNOWN"},
        mode = Mode.EXCLUDE)
    void shouldAcceptEnabledRecordType(final RecordType recordType) {
      // given
      TestSupport.setIndexingForRecordType(config.index, recordType, true);

      // when
      exporter.configure(context);

      // then
      final RecordFilter filter = context.getRecordFilter();
      assertThat(filter).isNotNull();
      assertThat(filter.acceptType(recordType))
          .as("record filter should accept record type based on configuration")
          .isTrue();
    }
  }

  @Nested
  final class TemplatesTest {
    @Test
    void shouldCreateComponentTemplate() {
      // given
      config.index.createTemplate = true;
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.export(mock(Record.class));

      // then
      verify(client).putComponentTemplate();
    }

    @Test
    void shouldNotCreateComponentTemplate() {
      // given
      config.index.createTemplate = false;
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.export(mock(Record.class));

      // then
      verify(client, never()).putComponentTemplate();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldPutValueTypeTemplate(final ValueType valueType) {
      // given
      config.index.createTemplate = true;
      TestSupport.setIndexingForValueType(config.index, valueType, true);
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.export(mock(Record.class));

      // then
      verify(client, times(1)).putIndexTemplate(valueType);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("io.camunda.zeebe.exporter.TestSupport#provideValueTypes")
    void shouldNotPutValueTypeTemplate(final ValueType valueType) {
      // given
      config.index.createTemplate = true;
      TestSupport.setIndexingForValueType(config.index, valueType, false);
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.export(mock(Record.class));

      // then
      verify(client, never()).putIndexTemplate(valueType);
    }
  }

  @Nested
  final class FlushTest {
    @Test
    void shouldFlushWhenClientDecides() {
      // given
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(false, true);

      // when
      exporter.export(mock(Record.class));
      exporter.export(mock(Record.class));

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldFlushWhenBulkDelayIsReached() {
      // given
      config.bulk.delay = 10;
      exporter.configure(context);
      exporter.open(controller);

      // when
      controller.runScheduledTasks(Duration.ofSeconds(10));

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldFlushOnClose() {
      // given
      exporter.configure(context);
      exporter.open(controller);

      // when
      exporter.close();

      // then
      verify(client, times(1)).flush();
    }

    @Test
    void shouldUpdateLastExportedPositionOnFlush() {
      // given
      final var record = ImmutableRecord.builder().withPosition(10L).build();
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(true);

      // when
      exporter.export(record);

      // then
      assertThat(controller.getPosition()).isEqualTo(10L);
    }

    @Test
    void shouldNotUpdatePositionOnFlushErrors() {
      // given
      final var record = ImmutableRecord.builder().withPosition(10L).build();
      exporter.configure(context);
      exporter.open(controller);
      when(client.shouldFlush()).thenReturn(true);

      // when
      doThrow(new ElasticsearchExporterException("failed to flush")).when(client).flush();

      // then
      assertThatCode(() -> exporter.export(record))
          .isInstanceOf(ElasticsearchExporterException.class);
      assertThat(controller.getPosition()).isEqualTo(-1L);
    }
  }

  @Nested
  final class ValidationTest {
    @Test
    void shouldNotAllowUnderscoreInIndexPrefix() {
      // given
      config.index.prefix = "i_am_invalid";

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(ints = {-1, 0})
    void shouldForbidNonPositiveNumberOfShards(final int invalidNumberOfShards) {
      // given
      config.index.setNumberOfShards(invalidNumberOfShards);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }

    @Test
    void shouldForbidNegativeNumberOfReplicas() {
      // given
      config.index.setNumberOfReplicas(-1);

      // when - then
      assertThatCode(() -> exporter.configure(context)).isInstanceOf(ExporterException.class);
    }
  }
}
