/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.metrics;

import static io.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static io.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static io.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.OptimizeRequestExecutor;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Statistic;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

@Disabled("Disabled due to issues with actuator config, to be fixed with OPT-7141")
public class ZeebeMetricsIT extends AbstractCCSMIT {

  @ParameterizedTest
  @MethodSource("metricRequesters")
  public void metricsAreCollected(final Supplier<OptimizeRequestExecutor> requester) {
    // given
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // when
    final MetricResponseDto response =
        requester.get().execute(MetricResponseDto.class, HttpStatus.OK.value());

    // then
    final Stream<String> actualTags =
        response.getAvailableTags().stream().map(MetricResponseDto.TagDto::getTag);
    assertThat(actualTags).contains(RECORD_TYPE_TAG, PARTITION_ID_TAG);

    validateResults(response);
  }

  @ParameterizedTest
  @MethodSource("metricRequesters")
  public void metricsAreCollectedByTags(final Supplier<OptimizeRequestExecutor> requester) {
    // given
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // when
    final MetricResponseDto response =
        requester
            .get()
            .addSingleQueryParam("tag", RECORD_TYPE_TAG + ":" + ValueType.PROCESS_INSTANCE)
            .execute(MetricResponseDto.class, HttpStatus.OK.value());

    // then
    final Stream<String> actualTags =
        response.getAvailableTags().stream().map(MetricResponseDto.TagDto::getTag);
    assertThat(actualTags).contains(PARTITION_ID_TAG);

    validateResults(response);
  }

  private void validateResults(final MetricResponseDto response) {
    MetricResponseDto.StatisticDto statistic = getStatistic(response, Statistic.TOTAL_TIME);
    final Double totalTime = statistic.getValue();
    assertThat(statistic).isNotNull();
    assertThat(totalTime).isGreaterThan(0L);

    statistic = getStatistic(response, Statistic.COUNT);
    assertThat(statistic).isNotNull();
    assertThat(statistic.getValue()).isGreaterThan(0L);

    statistic = getStatistic(response, Statistic.MAX);
    assertThat(statistic).isNotNull();
    assertThat(statistic.getValue()).isGreaterThan(0L).isLessThan(totalTime);
  }

  private MetricResponseDto.StatisticDto getStatistic(
      final MetricResponseDto response, final Statistic statistic) {
    return response.getMeasurements().stream()
        .filter(m -> m.getStatistic().equals(statistic))
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeIntegrationTestException(
                    "The response from actuator doesn't contain the requested metric"));
  }

  private static Stream<Supplier<OptimizeRequestExecutor>> metricRequesters() {
    return Stream.of(
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .setActuatorWebTarget()
                .buildIndexingTimeMetricRequest(),
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .setActuatorWebTarget()
                .buildPageFetchTimeMetricRequest(),
        () ->
            embeddedOptimizeExtension
                .getRequestExecutor()
                .setActuatorWebTarget()
                .buildOverallImportTimeMetricRequest());
  }
}
