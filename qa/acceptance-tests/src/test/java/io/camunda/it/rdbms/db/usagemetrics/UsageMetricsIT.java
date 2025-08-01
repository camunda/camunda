/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usagemetrics;

import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.EDI;
import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.RPI;
import static io.camunda.zeebe.util.HashUtil.getStringHashValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UsageMetricTUDbReader;
import io.camunda.db.rdbms.read.service.UsageMetricsDbReader;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel;
import io.camunda.db.rdbms.write.domain.UsageMetricTUDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricTUWriter;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.it.rdbms.db.fixtures.CommonFixtures;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity.UsageMetricTUStatisticsEntityTenant;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.filter.UsageMetricsFilter.Builder;
import io.camunda.search.filter.UsageMetricsTUFilter;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.search.query.UsageMetricsTUQuery;
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

  private static final long ASSIGNEE_HASH_1 = getStringHashValue("assignee1");
  private static final long ASSIGNEE_HASH_2 = getStringHashValue("assignee2");
  private static final long ASSIGNEE_HASH_3 = getStringHashValue("assignee3");
  private static final Long PARTITION_ID = 0L;
  private static final OffsetDateTime NOW = OffsetDateTime.now();
  private static final OffsetDateTime NOW_MINUS_5M = NOW.minusMinutes(5);
  private static final OffsetDateTime NOW_MINUS_10M = NOW.minusMinutes(10);
  private static final String TENANT1 = "tenant1";
  private static final String TENANT2 = "tenant2";

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriter rdbmsWriter;
  private UsageMetricsDbReader usageMetricReader;
  private UsageMetricTUDbReader usageMetricTUDbReader;
  private UsageMetricWriter usageMetricWriter;
  private UsageMetricTUWriter usageMetricTUWriter;

  private void writeMetric(
      final UsageMetricWriter usageMetricWriter,
      final EventTypeDbModel eventType,
      final OffsetDateTime time,
      final String tenantId,
      final long value) {
    usageMetricWriter.create(
        new UsageMetricDbModel.Builder()
            .key(CommonFixtures.nextKey())
            .eventTime(time)
            .tenantId(tenantId)
            .eventType(eventType)
            .value(value)
            .partitionId(PARTITION_ID.intValue())
            .build());
  }

  private void writeTUMetric(
      final UsageMetricTUWriter usageMetricTUWriter,
      final OffsetDateTime time,
      final String tenantId,
      final long value) {
    usageMetricTUWriter.create(
        new UsageMetricTUDbModel.Builder()
            .key(CommonFixtures.nextKey())
            .eventTime(time)
            .tenantId(tenantId)
            .assigneeHash(value)
            .partitionId(PARTITION_ID.intValue())
            .build());
  }

  @BeforeEach
  void setUp() {
    final RdbmsService rdbmsService = testApplication.getRdbmsService();
    rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    usageMetricReader = rdbmsService.getUsageMetricReader();
    usageMetricTUDbReader = rdbmsService.getUsageMetricTUReader();
    usageMetricWriter = rdbmsWriter.getUsageMetricWriter();
    usageMetricTUWriter = rdbmsWriter.getUsageMetricTUWriter();
  }

  @AfterEach
  void tearDown() {
    usageMetricWriter.cleanupMetrics(PARTITION_ID.intValue(), NOW.plusDays(1), Integer.MAX_VALUE);
    usageMetricTUWriter.cleanupMetrics(PARTITION_ID.intValue(), NOW.plusDays(1), Integer.MAX_VALUE);
  }

  @TestTemplate
  public void shouldAggregateMetricsWithTenant() {
    // given
    writeMetric(usageMetricWriter, RPI, NOW, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, NOW, TENANT1, 11L);
    writeMetric(usageMetricWriter, RPI, NOW_MINUS_5M, TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, NOW_MINUS_5M, TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_5M, TENANT2, 3L);
    writeMetric(usageMetricWriter, RPI, NOW_MINUS_10M, TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_10M, TENANT2, 3L);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            UsageMetricsQuery.of(q -> q.filter(f -> f.withTenants(true))), null);

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                19,
                17,
                2,
                Map.of(
                    TENANT1,
                    new UsageMetricStatisticsEntityTenant(13L, 11L),
                    TENANT2,
                    new UsageMetricStatisticsEntityTenant(6L, 6L))));
  }

  @TestTemplate
  public void shouldAggregateTUMetricsWithTenant() {
    // given
    writeTUMetric(usageMetricTUWriter, NOW, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT2, ASSIGNEE_HASH_3);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_3);
    rdbmsWriter.flush();

    // when
    final var actualTU =
        usageMetricTUDbReader.usageMetricTUStatistics(
            UsageMetricsTUQuery.of(q -> q.filter(f -> f.withTenants(true))), null);

    // then
    assertThat(actualTU)
        .isEqualTo(
            new UsageMetricTUStatisticsEntity(
                3,
                Map.of(
                    TENANT1,
                    new UsageMetricTUStatisticsEntityTenant(2L),
                    TENANT2,
                    new UsageMetricTUStatisticsEntityTenant(3L))));
  }

  @TestTemplate
  public void shouldAggregateMetricsWithoutTenant() {
    // given
    writeMetric(usageMetricWriter, RPI, NOW, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, NOW, TENANT1, 11L);
    writeMetric(usageMetricWriter, RPI, NOW_MINUS_5M, TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, NOW_MINUS_5M, TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_5M, TENANT2, 3L);
    rdbmsWriter.flush();

    // when
    final var actual = usageMetricReader.usageMetricStatistics(UsageMetricsQuery.of(q -> q), null);

    // then
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(16, 14, 2, null));
  }

  @TestTemplate
  public void shouldAggregateTUMetricsWithoutTenant() {
    // given
    writeTUMetric(usageMetricTUWriter, NOW, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW, TENANT1, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_3);
    rdbmsWriter.flush();

    // when
    final var actualTU =
        usageMetricTUDbReader.usageMetricTUStatistics(UsageMetricsTUQuery.of(q -> q), null);

    // then
    assertThat(actualTU).isEqualTo(new UsageMetricTUStatisticsEntity(3, null));
  }

  @TestTemplate
  public void shouldFilterMetricsByDate() {
    // given
    writeMetric(usageMetricWriter, RPI, NOW, TENANT1, 1L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_5M, TENANT1, 1L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_10M, TENANT2, 1L);
    rdbmsWriter.flush();

    // when
    final UsageMetricsFilter filter =
        new Builder().startTime(NOW.minusMinutes(6)).endTime(NOW.plusMinutes(6)).build();
    final var actual =
        usageMetricReader.usageMetricStatistics(UsageMetricsQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(1, 1, 1, null));
  }

  @TestTemplate
  public void shouldFilterTUMetricsByDate() {
    // given
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT1, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_2);
    rdbmsWriter.flush();

    // when
    final UsageMetricsTUFilter filter =
        new UsageMetricsTUFilter.Builder()
            .startTime(NOW.minusMinutes(6))
            .endTime(NOW.plusMinutes(6))
            .build();
    final var actualTU =
        usageMetricTUDbReader.usageMetricTUStatistics(
            UsageMetricsTUQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actualTU).isEqualTo(new UsageMetricTUStatisticsEntity(2, null));
  }

  @TestTemplate
  public void shouldFilterMetricsWithTenantByDate() {
    // given
    writeMetric(usageMetricWriter, RPI, NOW, TENANT1, 1L);
    writeMetric(usageMetricWriter, RPI, NOW, TENANT2, 1L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_5M, TENANT2, 1L);
    writeMetric(usageMetricWriter, EDI, NOW_MINUS_10M, TENANT2, 1L);
    rdbmsWriter.flush();

    // when
    final UsageMetricsFilter filter =
        new Builder()
            .startTime(NOW.minusMinutes(6))
            .endTime(NOW.plusMinutes(6))
            .withTenants(true)
            .build();
    final var actual =
        usageMetricReader.usageMetricStatistics(UsageMetricsQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                2,
                1,
                2,
                Map.of(
                    TENANT1, new UsageMetricStatisticsEntityTenant(1L, 0L),
                    TENANT2, new UsageMetricStatisticsEntityTenant(1L, 1L))));
  }

  @TestTemplate
  public void shouldFilterTUMetricsWithTenantByDate() {
    // given
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_2);
    rdbmsWriter.flush();

    // when
    final UsageMetricsTUFilter filter =
        new UsageMetricsTUFilter.Builder()
            .startTime(NOW.minusMinutes(6))
            .endTime(NOW.plusMinutes(6))
            .withTenants(true)
            .build();
    final var actualTU =
        usageMetricTUDbReader.usageMetricTUStatistics(
            UsageMetricsTUQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actualTU)
        .isEqualTo(
            new UsageMetricTUStatisticsEntity(
                1, Map.of(TENANT2, new UsageMetricTUStatisticsEntityTenant(1L))));
  }

  @TestTemplate
  public void shouldFilterMetricsWithTenantByTenantId() {
    // given
    writeMetric(usageMetricWriter, RPI, NOW, TENANT1, 1L);
    writeMetric(usageMetricWriter, RPI, NOW, TENANT2, 1L);
    rdbmsWriter.flush();

    // when
    final UsageMetricsFilter filter = new Builder().tenantId(TENANT1).withTenants(true).build();
    final var actual =
        usageMetricReader.usageMetricStatistics(UsageMetricsQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                1, 0, 1, Map.of(TENANT1, new UsageMetricStatisticsEntityTenant(1L, 0L))));
  }

  @TestTemplate
  public void shouldFilterTUMetricsWithTenantByTenantId() {
    // given
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_10M, TENANT1, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, NOW_MINUS_5M, TENANT2, ASSIGNEE_HASH_3);
    rdbmsWriter.flush();

    // when
    final UsageMetricsTUFilter filter =
        new UsageMetricsTUFilter.Builder().tenantId(TENANT1).withTenants(true).build();
    final var actualTU =
        usageMetricTUDbReader.usageMetricTUStatistics(
            UsageMetricsTUQuery.of(q -> q.filter(filter)), null);

    // then
    assertThat(actualTU)
        .isEqualTo(
            new UsageMetricTUStatisticsEntity(
                2, Map.of(TENANT1, new UsageMetricTUStatisticsEntityTenant(2L))));
  }
}
