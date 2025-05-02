/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.adapters.ClientAdapter;
import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.cache.form.CachedFormEntity;
import io.camunda.exporter.cache.process.CachedProcessEntity;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.CountAtBucket;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

final class CamundaExporterTest {
  private final MockedStatic<ClientAdapter> mockedClientAdapterFactory =
      Mockito.mockStatic(ClientAdapter.class);

  private final ExporterResourceProvider resourceProvider =
      spy(new DefaultExporterResourceProvider());
  private final ExporterConfiguration configuration = new ExporterConfiguration();
  private final ExporterTestContext testContext =
      new ExporterTestContext()
          .setConfiguration(new ExporterTestConfiguration<>("test", configuration));
  private final ExporterTestController testController = new ExporterTestController();
  private ClientAdapter stubbedClientAdapterInUse;

  @SuppressWarnings("FieldCanBeLocal")
  @AutoClose
  private CamundaExporter exporter;

  @BeforeEach
  void beforeEach() {
    stubbedClientAdapterInUse = new StubClientAdapter();
    mockedClientAdapterFactory
        .when(() -> ClientAdapter.of(configuration.getConnect()))
        .thenReturn(stubbedClientAdapterInUse);
    doReturn(emptyList()).when(resourceProvider).getIndexDescriptors();
    doReturn(emptyList()).when(resourceProvider).getIndexTemplateDescriptors();
  }

  @AfterEach
  void tearDown() {
    mockedClientAdapterFactory.close();
  }

  private static final class NoopExporterEntityCacheProvider
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<Long, CachedProcessEntity> getProcessCacheLoader(
        final String processIndexName) {
      return k -> null;
    }

    @Override
    public CacheLoader<String, CachedFormEntity> getFormCacheLoader(final String formIndexName) {
      return k -> null;
    }
  }

  private static final class StubClientAdapter implements ClientAdapter {
    private final ExporterEntityCacheProvider entityCacheProvider =
        new NoopExporterEntityCacheProvider();
    private final SearchEngineClient client =
        mock(
            SearchEngineClient.class,
            Mockito.withSettings().defaultAnswer(Answers.RETURNS_SMART_NULLS));

    @Override
    public ObjectMapper objectMapper() {
      return TestObjectMapper.objectMapper();
    }

    @Override
    public SearchEngineClient getSearchEngineClient() {
      return client;
    }

    @Override
    public BatchRequest createBatchRequest() {
      return mock(
          BatchRequest.class, Mockito.withSettings().defaultAnswer(Answers.RETURNS_SMART_NULLS));
    }

    @Override
    public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
      return entityCacheProvider;
    }

    @Override
    public void close() {}
  }

  @Nested
  final class OpenTest {
    @Test
    void shouldDeserializeMetadataOnOpen() {
      // given
      final var expected = new ExporterMetadata(TestObjectMapper.objectMapper());
      final var metadata = new ExporterMetadata(TestObjectMapper.objectMapper());
      exporter = new CamundaExporter(resourceProvider, metadata);
      expected.setLastIncidentUpdatePosition(3);
      expected.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, 5);
      expected.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 5);
      metadata.setLastIncidentUpdatePosition(-1);
      metadata.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, -1);
      metadata.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, -1);
      testController.updateLastExportedRecordPosition(-1, expected.serialize());

      // when
      exporter.configure(testContext);
      exporter.open(testController);

      // then
      assertThat(metadata.getLastIncidentUpdatePosition()).isEqualTo(3);
      assertThat(metadata.getFirstUserTaskKey(TaskImplementation.JOB_WORKER)).isEqualTo(5);
      assertThat(metadata.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK)).isEqualTo(5);
    }
  }

  @Nested
  final class FlushTest {
    @Test
    void shouldUpdateMetadataOnFlush() {
      // given
      final var expected = new ExporterMetadata(TestObjectMapper.objectMapper());
      exporter = new CamundaExporter(resourceProvider, expected);

      final var exporterEngineClient = stubbedClientAdapterInUse.getSearchEngineClient();
      when(exporterEngineClient.importersCompleted(anyInt(), any())).thenReturn(true);

      expected.setLastIncidentUpdatePosition(5);
      expected.setFirstUserTaskKey(TaskImplementation.JOB_WORKER, 10);
      expected.setFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK, 10);

      // when
      exporter.configure(testContext);
      exporter.open(testController);
      testController.runScheduledTasks(Duration.ofHours(1));

      // then
      final var actual = new ExporterMetadata(TestObjectMapper.objectMapper());
      testController.readMetadata().ifPresent(actual::deserialize);
      assertThat(actual.getLastIncidentUpdatePosition()).isEqualTo(5);
      assertThat(actual.getFirstUserTaskKey(TaskImplementation.JOB_WORKER)).isEqualTo(10);
      assertThat(actual.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK)).isEqualTo(10);
    }

    @Test
    void shouldObserveRecordExportDurationOnFlush() {
      // given
      final var protocolFactory = new ProtocolFactory();
      when(stubbedClientAdapterInUse.getSearchEngineClient().importersCompleted(anyInt(), any()))
          .thenReturn(true);
      exporter = new CamundaExporter(resourceProvider);
      exporter.configure(testContext);
      exporter.open(testController);

      // when
      exporter.export(
          protocolFactory.generateRecord(
              ValueType.VARIABLE, b -> b.withKey(1L).withTimestamp(1).withBrokerVersion("8.8.0")));
      exporter.export(
          protocolFactory.generateRecord(
              ValueType.VARIABLE, b -> b.withKey(2L).withTimestamp(5).withBrokerVersion("8.8.0")));

      testContext.pinClock(10L);
      testController.runScheduledTasks(Duration.ofHours(1));

      // then
      final var timer =
          testContext
              .getMeterRegistry()
              .get("zeebe.camunda.exporter.record.export.duration")
              .timer();
      final var histogramSnapshot = timer.takeSnapshot();
      assertThat(histogramSnapshot.count()).isEqualTo(2);
      assertThat(histogramSnapshot.max()).isEqualTo(Duration.ofMillis(9).toNanos());
      assertThat(histogramSnapshot.mean()).isEqualTo(Duration.ofMillis(7).toNanos());

      final var buckets = histogramSnapshot.histogramCounts();
      assertThat(buckets[0].count())
          .as("there is only one value in the lowest bucket <=5ms")
          .isOne();
      assertThat(buckets[1].count())
          .as("both values are captured in the next bucket <= 10ms")
          .isEqualTo(2);
    }

    @Test
    void shouldObserveRecordExportDurationMultipleFlush() {
      // given
      final var protocolFactory = new ProtocolFactory();
      when(stubbedClientAdapterInUse.getSearchEngineClient().importersCompleted(anyInt(), any()))
          .thenReturn(true);
      exporter = new CamundaExporter(resourceProvider);
      exporter.configure(testContext);
      exporter.open(testController);

      // when
      exporter.export(
          protocolFactory.generateRecord(
              ValueType.VARIABLE, b -> b.withKey(1L).withTimestamp(1).withBrokerVersion("8.8.0")));
      testContext.pinClock(5L);
      testController.runScheduledTasks(Duration.ofHours(1));

      exporter.export(
          protocolFactory.generateRecord(
              ValueType.VARIABLE, b -> b.withKey(2L).withTimestamp(5).withBrokerVersion("8.8.0")));
      testContext.pinClock(11L);
      testController.runScheduledTasks(Duration.ofHours(1));

      // then
      final var timer =
          testContext
              .getMeterRegistry()
              .get("zeebe.camunda.exporter.record.export.duration")
              .timer();
      final var histogramSnapshot = timer.takeSnapshot();
      assertThat(histogramSnapshot.count()).isEqualTo(2);
      assertThat(histogramSnapshot.max()).isEqualTo(Duration.ofMillis(6).toNanos());
      assertThat(histogramSnapshot.mean()).isEqualTo(Duration.ofMillis(5).toNanos());

      final var buckets = histogramSnapshot.histogramCounts();
      assertThat(buckets[0].count())
          .as("there is only one value in the lowest bucket <=5ms")
          .isOne();
      assertThat(buckets[1].count())
          .as("both values are captured in the next bucket <= 10ms")
          .isEqualTo(2);
    }
  }
}
