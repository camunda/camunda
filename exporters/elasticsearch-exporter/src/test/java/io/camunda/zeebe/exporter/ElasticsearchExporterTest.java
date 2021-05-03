/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static io.camunda.zeebe.exporter.ElasticsearchExporter.ZEEBE_RECORD_TEMPLATE_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.api.ExporterException;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.exporter.ExporterTestHarness;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

public class ElasticsearchExporterTest {

  private ExporterTestHarness testHarness;
  private ElasticsearchExporterConfiguration config;
  private ElasticsearchClient esClient;

  @Before
  public void setUp() {
    config = new ElasticsearchExporterConfiguration();
    esClient = mockElasticsearchClient();
  }

  @Test
  public void shouldNotFailOnOpenIfElasticIsUnreachable() {
    // given
    final ElasticsearchClient client =
        Mockito.spy(new ElasticsearchClient(config, LoggerFactory.getLogger("test")));
    final ElasticsearchExporter exporter = createExporter(client);
    config.index.createTemplate = true;

    // when - then : only fails when trying to export, not before
    openExporter(exporter);
    assertThatThrownBy(testHarness::export).isInstanceOf(ElasticsearchExporterException.class);
  }

  @Test
  public void shouldCreateIndexTemplates() {
    // given
    config.index.prefix = "foo-bar";
    config.index.createTemplate = true;
    config.index.deployment = true;
    config.index.process = true;
    config.index.error = true;
    config.index.incident = true;
    config.index.job = true;
    config.index.jobBatch = true;
    config.index.message = true;
    config.index.messageSubscription = true;
    config.index.variable = true;
    config.index.variableDocument = true;
    config.index.processInstance = true;
    config.index.processInstanceCreation = true;
    config.index.processMessageSubscription = true;

    // when
    createAndOpenExporter();
    testHarness.export();

    // then
    verify(esClient).putIndexTemplate("foo-bar", "foo-bar", ZEEBE_RECORD_TEMPLATE_JSON);

    verify(esClient).putIndexTemplate(ValueType.DEPLOYMENT);
    verify(esClient).putIndexTemplate(ValueType.PROCESS);
    verify(esClient).putIndexTemplate(ValueType.ERROR);
    verify(esClient).putIndexTemplate(ValueType.INCIDENT);
    verify(esClient).putIndexTemplate(ValueType.JOB);
    verify(esClient).putIndexTemplate(ValueType.JOB_BATCH);
    verify(esClient).putIndexTemplate(ValueType.MESSAGE);
    verify(esClient).putIndexTemplate(ValueType.MESSAGE_SUBSCRIPTION);
    verify(esClient).putIndexTemplate(ValueType.VARIABLE);
    verify(esClient).putIndexTemplate(ValueType.VARIABLE_DOCUMENT);
    verify(esClient).putIndexTemplate(ValueType.PROCESS_INSTANCE);
    verify(esClient).putIndexTemplate(ValueType.PROCESS_INSTANCE_CREATION);
    verify(esClient).putIndexTemplate(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
  }

  @Test
  public void shouldExportEnabledValueTypes() {
    // given
    config.index.event = true;
    config.index.deployment = true;
    config.index.process = true;
    config.index.error = true;
    config.index.incident = true;
    config.index.job = true;
    config.index.jobBatch = true;
    config.index.message = true;
    config.index.messageSubscription = true;
    config.index.variable = true;
    config.index.variableDocument = true;
    config.index.processInstance = true;
    config.index.processInstanceCreation = true;
    config.index.processMessageSubscription = true;

    createAndOpenExporter();

    final ValueType[] valueTypes =
        new ValueType[] {
          ValueType.DEPLOYMENT,
          ValueType.PROCESS,
          ValueType.ERROR,
          ValueType.INCIDENT,
          ValueType.JOB,
          ValueType.JOB_BATCH,
          ValueType.MESSAGE,
          ValueType.MESSAGE_SUBSCRIPTION,
          ValueType.VARIABLE,
          ValueType.VARIABLE_DOCUMENT,
          ValueType.PROCESS_INSTANCE,
          ValueType.PROCESS_INSTANCE_CREATION,
          ValueType.PROCESS_MESSAGE_SUBSCRIPTION
        };

    // when - then
    final Context.RecordFilter filter = testHarness.getContext().getFilter();

    assertThat(Arrays.stream(valueTypes).map(filter::acceptValue)).containsOnly(true);
  }

  @Test
  public void shouldNotExportDisabledValueTypes() {
    // given
    config.index.event = true;
    config.index.deployment = false;
    config.index.error = false;
    config.index.incident = false;
    config.index.job = false;
    config.index.jobBatch = false;
    config.index.message = false;
    config.index.messageSubscription = false;
    config.index.variable = false;
    config.index.variableDocument = false;
    config.index.processInstance = false;
    config.index.processInstanceCreation = false;
    config.index.processMessageSubscription = false;

    createAndOpenExporter();

    final ValueType[] valueTypes =
        new ValueType[] {
          ValueType.DEPLOYMENT,
          ValueType.ERROR,
          ValueType.INCIDENT,
          ValueType.JOB,
          ValueType.JOB_BATCH,
          ValueType.MESSAGE,
          ValueType.MESSAGE_SUBSCRIPTION,
          ValueType.VARIABLE,
          ValueType.VARIABLE_DOCUMENT,
          ValueType.PROCESS_INSTANCE,
          ValueType.PROCESS_INSTANCE_CREATION,
          ValueType.PROCESS_MESSAGE_SUBSCRIPTION
        };

    // when - then
    final Context.RecordFilter filter = testHarness.getContext().getFilter();

    assertThat(Arrays.stream(valueTypes).map(filter::acceptValue)).containsOnly(false);
  }

  @Test
  public void shouldExportEnabledRecordTypes() {
    // given
    config.index.command = true;
    config.index.event = true;
    config.index.rejection = true;
    config.index.deployment = true;

    createAndOpenExporter();

    final RecordType[] recordTypes =
        new RecordType[] {RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND_REJECTION};

    // when - then
    final Context.RecordFilter filter = testHarness.getContext().getFilter();

    assertThat(Arrays.stream(recordTypes).map(filter::acceptType)).containsOnly(true);
  }

  @Test
  public void shouldNotExportDisabledRecordTypes() {
    // given
    config.index.command = false;
    config.index.event = false;
    config.index.rejection = false;
    config.index.deployment = true;

    createAndOpenExporter();

    final RecordType[] recordTypes =
        new RecordType[] {RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND_REJECTION};

    // when - then
    final Context.RecordFilter filter = testHarness.getContext().getFilter();

    assertThat(Arrays.stream(recordTypes).map(filter::acceptType)).containsOnly(false);
  }

  @Test
  public void shouldUpdateLastPositionOnFlush() {
    // given
    when(esClient.shouldFlush()).thenReturn(true);

    // when
    createAndOpenExporter();
    final Record record =
        testHarness.export(
            r ->
                r.getMetadata()
                    .setValueType(ValueType.PROCESS_INSTANCE)
                    .setRecordType(RecordType.EVENT));

    // then
    assertThat(testHarness.getController().getPosition()).isEqualTo(record.getPosition());
  }

  @Test
  public void shouldFlushOnClose() {
    // given
    createAndOpenExporter();

    // when
    testHarness.close();

    // then
    verify(esClient).flush();
  }

  @Test
  public void shouldFlushAfterDelay() {
    // given
    config.index.event = true;
    config.index.processInstance = true;
    config.bulk.delay = 10;

    // scenario: bulk size is not reached still we want to flush
    config.bulk.size = Integer.MAX_VALUE;
    when(esClient.shouldFlush()).thenReturn(false);
    createAndOpenExporter();

    // when
    testHarness.export(
        r ->
            r.getMetadata()
                .setValueType(ValueType.PROCESS_INSTANCE)
                .setRecordType(RecordType.EVENT));

    // then
    assertThat(testHarness.getController().getScheduledTasks()).hasSize(1);
    assertThat(testHarness.getController().getScheduledTasks().get(0).getDelay())
        .isEqualTo(Duration.ofSeconds(config.bulk.delay));

    // and
    testHarness.getController().runScheduledTasks(Duration.ofSeconds(config.bulk.delay));
    verify(esClient).flush();
  }

  @Test
  public void shouldUpdatePositionAfterDelay() {
    // given
    config.index.event = true;
    createAndOpenExporter();

    // when
    final List<Record> exported =
        testHarness.stream(
                r ->
                    r.getMetadata()
                        .setValueType(ValueType.PROCESS_INSTANCE)
                        .setRecordType(RecordType.EVENT))
            .export(4);
    testHarness.getController().runScheduledTasks(Duration.ofSeconds(config.bulk.delay));

    // then record was indexed and the exporter record position was updated
    verify(esClient, times(4)).index(any());
    assertThat(testHarness.getController().getPosition()).isEqualTo(exported.get(3).getPosition());
  }

  @Test
  public void shouldNotHandleFlushException() {
    // given
    when(esClient.shouldFlush()).thenReturn(true);
    doThrow(new ElasticsearchExporterException("expected")).when(esClient).flush();

    createAndOpenExporter();

    // when
    assertThatThrownBy(() -> testHarness.export())
        .isInstanceOf(ElasticsearchExporterException.class)
        .withFailMessage("expected");

    // then
    verify(esClient, times(1)).flush();
  }

  @Test
  public void shouldFailOnWrongPrefix() {
    // given
    config.index.prefix = "prefix_withunderscore";

    createExporterAndTestHarness();

    // then
    assertThatThrownBy(() -> testHarness.configure("els", config))
        .isInstanceOf(ExporterException.class)
        .withFailMessage(
            "Elasticsearch prefix must not contain underscore. Current value: "
                + config.index.prefix);
  }

  private ElasticsearchExporter createExporter() {
    return createExporter(esClient);
  }

  private ElasticsearchExporter createExporter(final ElasticsearchClient client) {
    return new ElasticsearchExporter() {
      @Override
      protected ElasticsearchClient createClient() {
        return client;
      }
    };
  }

  private void openExporter(final ElasticsearchExporter exporter) {
    testHarness = new ExporterTestHarness(exporter);
    try {
      testHarness.configure("elasticsearch", config);
    } catch (final Exception e) {
      throw new AssertionError("Failed to configure exporter", e);
    }
    testHarness.open();
  }

  private ElasticsearchExporter createAndOpenExporter() {
    final ElasticsearchExporter exporter = createExporter();
    openExporter(exporter);
    return exporter;
  }

  private ElasticsearchExporter createExporterAndTestHarness() {
    final ElasticsearchExporter exporter = createExporter();
    testHarness = new ExporterTestHarness(exporter);
    return exporter;
  }

  private ElasticsearchClient mockElasticsearchClient() {
    final ElasticsearchClient client = mock(ElasticsearchClient.class);
    when(client.putIndexTemplate(any(ValueType.class))).thenReturn(true);
    when(client.putIndexTemplate(anyString(), anyString(), anyString())).thenReturn(true);
    return client;
  }
}
