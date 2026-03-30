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
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.schema.SearchEngineClient;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.entities.usertask.TaskEntity.TaskImplementation;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
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
  private final ProtocolFactory factory = new ProtocolFactory();

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

  private Record<?> stubRecord() {
    final var record = spy(factory.generateRecord(ValueType.VARIABLE));
    when(record.getBrokerVersion()).thenReturn("8.8.0");
    return record;
  }

  private static final class NoopExporterEntityCacheProvider
      implements ExporterEntityCacheProvider {

    @Override
    public CacheLoader<String, CachedBatchOperationEntity> getBatchOperationCacheLoader(
        final String batchOperationIndexName) {
      return k -> null;
    }

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
      return mock(BatchRequest.class, Mockito.withSettings().defaultAnswer(Answers.RETURNS_SELF));
    }

    @Override
    public ExporterEntityCacheProvider getExporterEntityCacheProvider() {
      return entityCacheProvider;
    }

    @Override
    public void close() {}
  }

  private static final class MutableClock implements InstantSource {
    private long millis;

    MutableClock(final long initialMillis) {
      millis = initialMillis;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis);
    }

    void advance(final long addMillis) {
      millis += addMillis;
    }
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
      final var clock = new MutableClock(1000);
      testContext.setClock(clock);
      configuration.getBulk().setDelay(1);

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

      clock.advance(1001);
      testController.runScheduledTasks(Duration.ofHours(1));

      // then
      final var actual = new ExporterMetadata(TestObjectMapper.objectMapper());
      testController.readMetadata().ifPresent(actual::deserialize);
      assertThat(actual.getLastIncidentUpdatePosition()).isEqualTo(5);
      assertThat(actual.getFirstUserTaskKey(TaskImplementation.JOB_WORKER)).isEqualTo(10);
      assertThat(actual.getFirstUserTaskKey(TaskImplementation.ZEEBE_USER_TASK)).isEqualTo(10);
    }
  }

  @Nested
  final class ScheduledFlushTest {

    @BeforeEach
    void setup() {
      configuration.getIndex().setShouldWaitForImporters(false);
    }

    @Test
    void shouldScheduleInitialFlushAtConfiguredDelay() {
      // given
      configuration.getBulk().setDelay(5);
      exporter =
          new CamundaExporter(
              resourceProvider, new ExporterMetadata(TestObjectMapper.objectMapper()));

      // when
      exporter.configure(testContext);
      exporter.open(testController);

      // then — delay(5) means 5 seconds, not 5 milliseconds
      final var tasks = testController.getScheduledTasks();
      assertThat(tasks).hasSize(1);
      assertThat(tasks.getFirst().getDelay()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void shouldNotFlushBeforeDelayElapses() {
      // given — use a controllable clock so we can verify timing
      final var clock = new MutableClock(1000);
      testContext.setClock(clock);
      // bulk size = 1 so that export() triggers an immediate size-based flush,
      // which sets lastFlushTimestamp without needing the scheduled task to flush
      configuration.getBulk().setSize(1);
      configuration.getBulk().setDelay(1);
      exporter =
          new CamundaExporter(
              resourceProvider, new ExporterMetadata(TestObjectMapper.objectMapper()));
      exporter.configure(testContext);
      exporter.open(testController);

      // export at t=500 — triggers immediate size-based flush, setting lastFlushTimestamp=500
      clock.advance(500);
      exporter.export(stubRecord());

      // run the scheduled task at t=800 — only 300ms since last flush, guard should skip
      clock.advance(300);
      testController.runScheduledTasks(Duration.ofSeconds(2));

      // then — the rescheduled task should have the remaining 700ms delay
      final var tasks = testController.getScheduledTasks();
      final var lastTask = tasks.getLast();
      assertThat(lastTask.getDelay()).isEqualTo(Duration.ofMillis(700));
    }

    @Test
    void shouldFlushWhenExportIfDelayElapsed() {
      // given — use a controllable clock so we can verify timing
      final var clock = new MutableClock(1000);
      testContext.setClock(clock);
      // bulk size = 10 so that a single export does NOT trigger a size-based flush;
      // flushing only happens via the scheduled task
      configuration.getBulk().setSize(10);
      configuration.getBulk().setDelay(1);
      exporter =
          new CamundaExporter(
              resourceProvider, new ExporterMetadata(TestObjectMapper.objectMapper()));
      exporter.configure(testContext);
      exporter.open(testController);

      // export at t=500 — batch size < 10, no size-based flush
      clock.advance(500);
      final var record1 =
          factory.generateRecord(
              ValueType.VARIABLE, b -> b.withPosition(1L).withBrokerVersion("8.8.0"));
      exporter.export(record1);

      // advance clock to trigger scheduled flush task at t=1000
      clock.advance(500);
      testController.runScheduledTasks(Duration.ofSeconds(1));

      // then — validate record1 was exported (position updated after timed flush)
      assertThat(testController.getPosition())
          .as("Record1 should have been flushed and position updated")
          .isEqualTo(record1.getPosition());

      // advance clock beyond delay to exceed the flush duration
      clock.advance(1001);

      // next export record with a higher position
      // batch size still < 10, but clock has advanced, so should flush
      final var record2 =
          factory.generateRecord(
              ValueType.VARIABLE, b -> b.withPosition(2L).withBrokerVersion("8.8.0"));
      exporter.export(record2);

      // then — validate record2 was exported (position updated after flush)
      assertThat(testController.getPosition())
          .as("Record2 should have been flushed and position updated")
          .isEqualTo(record2.getPosition());
    }

    @Test
    void shouldKeepStableDelayAcrossMultipleFlushCycles() {
      // Regression test: the rescheduled flush delay must remain stable across
      // repeated flush cycles and not degrade to zero.

      // given
      final var clock = new MutableClock(1000);
      testContext.setClock(clock);
      configuration.getBulk().setSize(1); // every export() triggers size-based flush
      configuration.getBulk().setDelay(1); // 1 second
      exporter =
          new CamundaExporter(
              resourceProvider, new ExporterMetadata(TestObjectMapper.objectMapper()));
      exporter.configure(testContext);
      exporter.open(testController);

      // when — four cycles of: advance 500ms, export (size flush), advance 500ms, run tasks
      for (int cycle = 0; cycle < 4; cycle++) {
        clock.advance(500);
        exporter.export(stubRecord()); // size-based flush, sets lastFlushTimestamp
        clock.advance(500);
        testController.runScheduledTasks(Duration.ofHours(1));

        // then — every cycle must retain a stable 500ms delay
        final var tasks = testController.getScheduledTasks();
        assertThat(tasks.getLast().getDelay())
            .as("Rescheduled delay in cycle %d must not degrade", cycle)
            .isEqualTo(Duration.ofMillis(500));
      }
    }

    @Test
    void shouldKeepStableDelayAcrossMultipleFlushCyclesWhenBatchIsEmpty() {
      // Regression test: the rescheduled flush delay must remain stable across
      // repeated flush cycles and not degrade to zero.

      // given
      final var clock = new MutableClock(1000);
      testContext.setClock(clock);
      configuration.getBulk().setDelay(1); // 1 second
      configuration.getIndex().setShouldWaitForImporters(false); // do not wait for importers
      exporter =
          new CamundaExporter(
              resourceProvider, new ExporterMetadata(TestObjectMapper.objectMapper()));
      exporter.configure(testContext);
      exporter.open(testController);

      // when
      for (int cycle = 0; cycle < 4; cycle++) {
        testController.runScheduledTasks(Duration.ofSeconds(2));
        clock.advance(1000);

        // then
        final var tasks = testController.getScheduledTasks();
        assertThat(tasks.getLast().getDelay()).isEqualTo(Duration.ofMillis(1000));
      }
    }
  }
}
