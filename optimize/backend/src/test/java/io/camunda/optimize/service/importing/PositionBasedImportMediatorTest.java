/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.MetricEnum.IMPORTED_UNTIL_METRIC;
import static io.camunda.optimize.MetricEnum.IMPORT_MEDIATOR_ERROR_METRIC;
import static io.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static io.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.importing.engine.mediator.MediatorRank;
import io.camunda.optimize.service.importing.engine.service.ImportService;
import io.camunda.optimize.service.importing.zeebe.handler.ZeebeVariableImportIndexHandler;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class PositionBasedImportMediatorTest {

  private static final int MAX_PAGE_SIZE = 3;
  private static final String VARIABLE = ValueType.VARIABLE.name();
  private static final OffsetDateTime FETCH_TIME =
      OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
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
  void shouldReportNowAsImportedUntilWhenThereIsNothingToImport() {
    // given
    final var recordReader = recordReaderFetching(List::of);

    // when
    recordReader.runImport().join();

    // then
    assertThat(importedUntil()).isEqualTo(FETCH_TIME.toEpochSecond());
  }

  @Test
  void shouldReportLastImportedRecordAsImportedUntil() {
    // given
    final List<ZeebeVariableRecordDto> page = records(2);
    final var recordReader = recordReaderFetching(() -> page);

    // when
    recordReader.runImport().join();

    // then
    assertThat(importedUntil()).isEqualTo(page.getLast().getTimestamp() / 1000);
  }

  @Test
  void shouldNotAdvanceImportedUntilWhileAPageIsStillBeingWritten() {
    // given
    final Deque<List<ZeebeVariableRecordDto>> pages = new ArrayDeque<>();
    pages.add(List.of());
    pages.add(records(2));
    final var recordReader = recordReaderFetching(pages::poll);
    doAnswer(invocation -> null).when(recordReader.importService).executeImport(any(), any());
    recordReader.runImport().join();

    // when
    LocalDateUtil.setCurrentTime(FETCH_TIME.plusMinutes(5));
    recordReader.runImport();

    // then
    assertThat(importedUntil()).isEqualTo(FETCH_TIME.toEpochSecond());
  }

  @Test
  void shouldReportRecordReaderErrorsBeforeTheFirstError() {
    // given
    final var recordReader = recordReaderFetching(List::of);

    // when
    recordReader.runImport().join();

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

  private double importedUntil() {
    return registry
        .get(IMPORTED_UNTIL_METRIC.getName())
        .tag(RECORD_TYPE_TAG, VARIABLE)
        .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
        .gauge()
        .value();
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
  private TestRecordReader recordReaderFetching(final Supplier<List<ZeebeVariableRecordDto>> page) {
    final ImportService<ZeebeVariableRecordDto> importService = mock(ImportService.class);
    doAnswer(
            invocation -> {
              invocation.<Runnable>getArgument(1).run();
              return null;
            })
        .when(importService)
        .executeImport(any(), any());
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, Answers.RETURNS_DEEP_STUBS);
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .thenReturn(MAX_PAGE_SIZE);
    return new TestRecordReader(page, importService, configurationService);
  }

  private final class TestRecordReader
      extends PositionBasedImportMediator<ZeebeVariableImportIndexHandler, ZeebeVariableRecordDto> {

    private final Supplier<List<ZeebeVariableRecordDto>> page;

    private TestRecordReader(
        final Supplier<List<ZeebeVariableRecordDto>> page,
        final ImportService<ZeebeVariableRecordDto> importService,
        final ConfigurationService configurationService) {
      this.page = page;
      this.importService = importService;
      this.configurationService = configurationService;
      importIndexHandler = mock(ZeebeVariableImportIndexHandler.class);
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
}
