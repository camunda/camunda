/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usagemetrics;

import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.*;
import static org.assertj.core.api.Assertions.*;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UsageMetricReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.filter.UsageMetricsFilter;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("rdbms")
@ExtendWith(CamundaRdbmsInvocationContextProviderExtension.class)
public class UsageMetricsIT {

  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final String TENANT1 = "tenant1";
  public static final String TENANT2 = "tenant2";

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriter rdbmsWriter;
  private UsageMetricReader usageMetricReader;
  private UsageMetricWriter usageMetricWriter;

  private void writeMetric(
      final UsageMetricWriter usageMetricWriter,
      final EventTypeDbModel eventType,
      final OffsetDateTime time,
      final String tenantId,
      final long value) {
    usageMetricWriter.create(
        new UsageMetricDbModel.Builder()
            .id(CommonFixtures.nextStringId())
            .eventTime(time)
            .tenantId(tenantId)
            .eventType(eventType)
            .value(value)
            .partitionId(PARTITION_ID.intValue())
            .build());
  }

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    usageMetricReader = rdbmsService.getUsageMetricReader();
    usageMetricWriter = rdbmsWriter.getUsageMetricWriter();
  }

  @AfterEach
  void tearDown() {
    usageMetricWriter.cleanupMetrics(
        PARTITION_ID.intValue(), OffsetDateTime.now().plusDays(1), Integer.MAX_VALUE);
  }

  @TestTemplate
  public void shouldAggregateMetricsWithTenant() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 3L);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            new UsageMetricsFilter.Builder().withTenants(true).build());

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                16,
                14,
                2,
                Map.of(
                    "tenant1",
                    new UsageMetricStatisticsEntityTenant(13L, 11L),
                    "tenant2",
                    new UsageMetricStatisticsEntityTenant(3L, 3L))));
  }

  @TestTemplate
  public void shouldAggregateMetricsWithoutTenant() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 3L);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(new UsageMetricsFilter.Builder().build());

    // then
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(16, 14, 2, null));
  }

  @TestTemplate
  public void shouldFilterMetricsByDate() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 1L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 1L);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            new UsageMetricsFilter.Builder()
                .startTime(now.minusMinutes(1))
                .endTime(now.plusMinutes(1))
                .build());

    // then
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(1, 0, 1, null));
  }

  @TestTemplate
  public void shouldFilterMetricsWithTenantByDate() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 1L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 1L);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            new UsageMetricsFilter.Builder()
                .startTime(now.minusMinutes(1))
                .endTime(now.plusMinutes(1))
                .withTenants(true)
                .build());

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                1, 0, 1, Map.of(TENANT1, new UsageMetricStatisticsEntityTenant(1L, 0L))));
  }
}
