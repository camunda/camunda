/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metrics;

import io.camunda.zeebe.protocol.record.ValueType;
import io.micrometer.core.instrument.Statistic;
import lombok.SneakyThrows;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.ws.rs.core.Response;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.OptimizeMetrics.PARTITION_ID_TAG;
import static org.camunda.optimize.OptimizeMetrics.RECORD_TYPE_TAG;
import static org.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;

public class ZeebeMetricsIT extends AbstractZeebeIT {
  @SneakyThrows
  @ParameterizedTest
  @MethodSource("metricRequesters")
  public void metricsAreCollected(Supplier<OptimizeRequestExecutor> requester) {
    // given
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // when
    MetricResponseDto response = requester.get()
      .execute(MetricResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    Stream<String> actualTags = response.getAvailableTags().stream().map(MetricResponseDto.TagDto::getTag);
    assertThat(actualTags).contains(RECORD_TYPE_TAG, PARTITION_ID_TAG);

    validateResults(response);
  }

  @SneakyThrows
  @ParameterizedTest
  @MethodSource("metricRequesters")
  public void metricsAreCollectedByTags(Supplier<OptimizeRequestExecutor> requester) {
    // given
    deployAndStartInstanceForProcess(createStartEndProcess("someProcess"));
    waitUntilMinimumProcessInstanceEventsExportedCount(1);
    importAllZeebeEntitiesFromScratch();

    // when
    MetricResponseDto response = requester.get()
      .addSingleQueryParam("tag", RECORD_TYPE_TAG + ":" + ValueType.PROCESS_INSTANCE)
      .execute(MetricResponseDto.class, Response.Status.OK.getStatusCode());

    // then
    Stream<String> actualTags = response.getAvailableTags().stream().map(MetricResponseDto.TagDto::getTag);
    assertThat(actualTags).contains(PARTITION_ID_TAG);

    validateResults(response);
  }

  private void validateResults(MetricResponseDto response) {
    MetricResponseDto.StatisticDto statistic = getStatistic(response, Statistic.TOTAL_TIME);
    Double totalTime = statistic.getValue();
    assertThat(statistic).isNotNull();
    assertThat(totalTime).isGreaterThan(0L);

    statistic = getStatistic(response, Statistic.COUNT);
    assertThat(statistic).isNotNull();
    assertThat(statistic.getValue()).isGreaterThan(0L);

    statistic = getStatistic(response, Statistic.MAX);
    assertThat(statistic).isNotNull();
    assertThat(statistic.getValue()).isGreaterThan(0L).isLessThan(totalTime);
  }

  private MetricResponseDto.StatisticDto getStatistic(MetricResponseDto response, Statistic statistic) {
    return response.getMeasurements()
      .stream()
      .filter(m -> m.getStatistic().equals(statistic))
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException("The response from actuator doesn't contain the requested metric"));
  }

  private static Stream<Supplier<OptimizeRequestExecutor>> metricRequesters() {
    return Stream.of(
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .setActuatorWebTarget()
        .buildIndexingTimeMetricRequest(),
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .setActuatorWebTarget()
        .buildPageFetchTimeMetricRequest(),
      () -> embeddedOptimizeExtension.getRequestExecutor()
        .setActuatorWebTarget()
        .buildOverallImportTimeMetricRequest()
    );
  }
}
