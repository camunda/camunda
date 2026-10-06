/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.MetricEnum.ERROR_METRIC;
import static io.camunda.optimize.MetricEnum.FETCH_PAGE_SIZE_METRIC;
import static io.camunda.optimize.MetricEnum.IMPORTED_UNTIL_METRIC;
import static io.camunda.optimize.MetricEnum.IMPORT_DB_WRITE_RETRIES_METRIC;
import static io.camunda.optimize.MetricEnum.IMPORT_MEDIATOR_ERROR_METRIC;
import static io.camunda.optimize.MetricEnum.ZEEBE_INDEX_MISSING_METRIC;
import static io.camunda.optimize.OptimizeMetrics.ERROR_TYPE_TAG;
import static io.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static io.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.ErrorType;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.importing.zeebe.fetcher.AbstractZeebeRecordFetcher;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class ImportMetricsTest {

  private static final int MAX_PAGE_SIZE = 3;
  private static final String VARIABLE = ValueType.VARIABLE.name();
  private static final OffsetDateTime FETCH_TIME =
      OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  // each test reports under its own partition, as metrics are global and outlive a test
  private final int partitionId = ThreadLocalRandom.current().nextInt(1_000, Integer.MAX_VALUE);

  @BeforeEach
  void setUp() {
    Metrics.addRegistry(registry);
    LocalDateUtil.setCurrentTime(FETCH_TIME);
  }

  @AfterEach
  void tearDown() {
    Metrics.removeRegistry(registry);
    registry.close();
    LocalDateUtil.reset();
  }

  @Test
  void shouldCountEveryFailedWriteOfAnImportPage() {
    // given - a job that fails twice before its write succeeds
    final var job = new FailingImportJob(2);
    job.setRecordSource(VARIABLE, partitionId);
    job.setEntitiesToImport(List.of(mock(OptimizeDto.class)));
    final double errorsBefore = errorCount(ErrorType.CLUSTER_BLOCK);

    // when
    job.run();

    // then
    assertThat(
            registry
                .get(IMPORT_DB_WRITE_RETRIES_METRIC.getName())
                .tag(RECORD_TYPE_TAG, VARIABLE)
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .tag(ERROR_TYPE_TAG, ErrorType.CLUSTER_BLOCK.getValue())
                .counter()
                .count())
        .isEqualTo(2);
    assertThat(errorCount(ErrorType.CLUSTER_BLOCK)).isEqualTo(errorsBefore + 2);
  }

  @Test
  void shouldReportFetchTimeAsImportedUntilWhenThePageIsNotFull() {
    // given
    final var mediator = mediatorFetching(() -> records(MAX_PAGE_SIZE - 1));

    // when
    mediator.runImport().join();

    // then - every record exported before the fetch is imported
    assertThat(importedUntil()).isEqualTo(FETCH_TIME.toEpochSecond());
  }

  @Test
  void shouldReportFetchTimeAsImportedUntilWhenThereIsNothingToImport() {
    // given
    final var mediator = mediatorFetching(List::of);

    // when
    mediator.runImport().join();

    // then
    assertThat(importedUntil()).isEqualTo(FETCH_TIME.toEpochSecond());
  }

  @Test
  void shouldReportLastRecordAsImportedUntilWhenThePageIsFull() {
    // given - a full page means more records may be waiting after its last one
    final List<ZeebeVariableRecordDto> page = records(MAX_PAGE_SIZE);
    final var mediator = mediatorFetching(() -> page);

    // when
    mediator.runImport().join();

    // then
    assertThat(importedUntil()).isEqualTo(page.getLast().getTimestamp() / 1000);
  }

  @Test
  void shouldReportLastPersistedRecordAsImportedUntilWhileTheFirstPageIsStillImporting() {
    // given - a restarted mediator whose first page never finishes, e.g. as writes keep failing
    final OffsetDateTime lastPersistedBeforeRestart = FETCH_TIME.minusHours(1);
    final var mediator = mediatorFetching(() -> records(MAX_PAGE_SIZE - 1));
    when(mediator.getImportIndexHandler().getTimestampOfLastPersistedEntity())
        .thenReturn(lastPersistedBeforeRestart);
    doAnswer(invocation -> null).when(mediator.importService).executeImport(any(), any());

    // when
    mediator.runImport();

    // then - lag keeps growing from the last record imported before the restart
    assertThat(importedUntil()).isEqualTo(lastPersistedBeforeRestart.toEpochSecond());
  }

  @Test
  void shouldReportMediatorErrorsBeforeTheFirstError() {
    // given
    final var mediator = mediatorFetching(List::of);

    // when
    mediator.runImport().join();

    // then
    assertThat(
            registry
                .get(IMPORT_MEDIATOR_ERROR_METRIC.getName())
                .tag(RECORD_TYPE_TAG, VARIABLE)
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .counter()
                .count())
        .isZero();
  }

  @Test
  void shouldCountFetchesThatFindNoZeebeIndex() {
    // given
    final var fetcher =
        new TestFetcher(partitionId, new OptimizeRuntimeException("index_not_found"), true);

    // when
    fetcher.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage());

    // then
    assertThat(
            registry
                .get(ZEEBE_INDEX_MISSING_METRIC.getName())
                .tag(RECORD_TYPE_TAG, ValueType.PROCESS_INSTANCE.name())
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void shouldReportTheReducedFetchPageSize() {
    // given
    final var fetcher = new TestFetcher(partitionId, new IOException("timeout"), false);

    // when
    assertThatExceptionOfType(OptimizeRuntimeException.class)
        .isThrownBy(
            () -> fetcher.getZeebeRecordsForPrefixAndPartitionFrom(new PositionBasedImportPage()));

    // then
    assertThat(
            registry
                .get(FETCH_PAGE_SIZE_METRIC.getName())
                .tag(RECORD_TYPE_TAG, ValueType.PROCESS_INSTANCE.name())
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .gauge()
                .value())
        .isEqualTo(MAX_PAGE_SIZE / 2);
  }

  private double importedUntil() {
    return registry
        .get(IMPORTED_UNTIL_METRIC.getName())
        .tag(RECORD_TYPE_TAG, VARIABLE)
        .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
        .gauge()
        .value();
  }

  private double errorCount(final ErrorType errorType) {
    return registry
        .get(ERROR_METRIC.getName())
        .tag(ERROR_TYPE_TAG, errorType.getValue())
        .counter()
        .count();
  }

  private List<ZeebeVariableRecordDto> records(final int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i -> {
              final ZeebeVariableRecordDto record = new ZeebeVariableRecordDto();
              record.setValueType(ValueType.VARIABLE);
              record.setPartitionId(partitionId);
              record.setPosition(i);
              record.setTimestamp(FETCH_TIME.minusMinutes(10 - i).toInstant().toEpochMilli());
              return record;
            })
        .toList();
  }

  @SuppressWarnings("unchecked")
  private TestMediator mediatorFetching(final Supplier<List<ZeebeVariableRecordDto>> page) {
    final ImportService<ZeebeVariableRecordDto> importService = mock(ImportService.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(importService)
        .executeImport(any(), any());
    return new TestMediator(page, importService, configurationService());
  }

  private static ConfigurationService configurationService() {
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, Answers.RETURNS_DEEP_STUBS);
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .thenReturn(MAX_PAGE_SIZE);
    return configurationService;
  }

  private static final class FailingImportJob extends DatabaseImportJob<OptimizeDto> {

    private int failuresLeft;

    private FailingImportJob(final int failures) {
      super(() -> {}, mock(DatabaseClient.class));
      failuresLeft = failures;
    }

    @Override
    protected void persistEntities(final List<OptimizeDto> newOptimizeEntities) {
      if (failuresLeft-- > 0) {
        throw new OptimizeRuntimeException("cluster_block_exception index [a] blocked");
      }
    }
  }

  private final class TestMediator
      extends PositionBasedImportMediator<ZeebeVariableImportIndexHandler, ZeebeVariableRecordDto> {

    private final Supplier<List<ZeebeVariableRecordDto>> page;

    private TestMediator(
        final Supplier<List<ZeebeVariableRecordDto>> page,
        final ImportService<ZeebeVariableRecordDto> importService,
        final ConfigurationService configurationService) {
      this.page = page;
      this.importService = importService;
      this.configurationService = configurationService;
      importIndexHandler = mock(ZeebeVariableImportIndexHandler.class);
      when(importIndexHandler.getTimestampOfLastPersistedEntity()).thenReturn(FETCH_TIME);
      idleBackoffCalculator = new BackoffCalculator(1L, 30L);
    }

    @Override
    public MediatorRank getRank() {
      return MediatorRank.INSTANCE_SUB_ENTITIES;
    }

    @Override
    protected boolean importNextPage(final Runnable importCompleteCallback) {
      return importNextPagePositionBased(page.get(), importCompleteCallback);
    }

    @Override
    protected String getRecordType() {
      return VARIABLE;
    }

    @Override
    protected Integer getPartitionId() {
      return partitionId;
    }
  }

  private static final class TestFetcher
      extends AbstractZeebeRecordFetcher<ZeebeVariableRecordDto> {

    private final Exception fetchFailure;
    private final boolean indexMissing;

    private TestFetcher(
        final int partitionId, final Exception fetchFailure, final boolean indexMissing) {
      super(partitionId, configurationService());
      this.fetchFailure = fetchFailure;
      this.indexMissing = indexMissing;
    }

    @Override
    protected boolean isZeebeInstanceIndexNotFoundException(final Exception e) {
      return indexMissing;
    }

    @Override
    protected List<ZeebeVariableRecordDto> fetchZeebeRecordsForPrefixAndPartitionFrom(
        final PositionBasedImportPage positionBasedImportPage) throws Exception {
      throw fetchFailure;
    }

    @Override
    protected String getBaseIndexName() {
      return "process-instance";
    }

    @Override
    protected Class<ZeebeVariableRecordDto> getRecordDtoClass() {
      return ZeebeVariableRecordDto.class;
    }
  }
}
