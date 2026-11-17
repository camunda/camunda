/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.fetcher;

import static io.camunda.optimize.MetricEnum.FETCH_PAGE_SIZE_METRIC;
import static io.camunda.optimize.MetricEnum.ZEEBE_INDEX_MISSING_METRIC;
import static io.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static io.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.importing.page.PositionBasedImportPage;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;

class AbstractZeebeRecordFetcherTest {

  private static final int MAX_PAGE_SIZE = 4;
  private static final String PROCESS_INSTANCE = ValueType.PROCESS_INSTANCE.name();

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final int partitionId = ThreadLocalRandom.current().nextInt(1_000, Integer.MAX_VALUE);

  @BeforeEach
  void setUp() {
    Metrics.addRegistry(registry);
  }

  @AfterEach
  void tearDown() {
    Metrics.removeRegistry(registry);
    registry.close();
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
                .tag(RECORD_TYPE_TAG, PROCESS_INSTANCE)
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
                .tag(RECORD_TYPE_TAG, PROCESS_INSTANCE)
                .tag(PARTITION_ID_TAG, String.valueOf(partitionId))
                .gauge()
                .value())
        .isEqualTo(MAX_PAGE_SIZE / 2);
  }

  private static ConfigurationService configurationService() {
    final ConfigurationService configurationService =
        mock(ConfigurationService.class, Answers.RETURNS_DEEP_STUBS);
    when(configurationService.getConfiguredZeebe().getMaxImportPageSize())
        .thenReturn(MAX_PAGE_SIZE);
    return configurationService;
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
