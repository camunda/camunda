/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usagemetrics;

import static io.camunda.db.rdbms.write.domain.UsageMetricDbModel.EventTypeDbModel.*;
import static io.camunda.zeebe.engine.utils.HashUtils.getStringHashValue;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.read.service.UsageMetricReader;
import io.camunda.db.rdbms.read.service.UsageMetricTUReader;
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
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.search.filter.UsageMetricsFilter.Builder;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
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
  public static final Long PARTITION_ID = 0L;
  public static final OffsetDateTime NOW = OffsetDateTime.now();
  public static final String TENANT1 = "tenant1";
  public static final String TENANT2 = "tenant2";

  private CamundaRdbmsTestApplication testApplication;
  private RdbmsWriter rdbmsWriter;
  private UsageMetricReader usageMetricReader;
  private UsageMetricTUReader usageMetricTUReader;
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
    usageMetricTUReader = rdbmsService.getUsageMetricTUReader();
    usageMetricWriter = rdbmsWriter.getUsageMetricWriter();
    usageMetricTUWriter = rdbmsWriter.getUsageMetricTUWriter();
  }

  @AfterEach
  void tearDown() {
    usageMetricWriter.cleanupMetrics(
        PARTITION_ID.intValue(), OffsetDateTime.now().plusDays(1), Integer.MAX_VALUE);
    usageMetricTUWriter.cleanupMetrics(
        PARTITION_ID.intValue(), OffsetDateTime.now().plusDays(1), Integer.MAX_VALUE);
  }

  @TestTemplate
  public void shouldAggregateMetricsWithTenant() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, ATU, now, TENANT1, 1L);
    writeTUMetric(usageMetricTUWriter, now, TENANT1, ASSIGNEE_HASH_1);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(5), TENANT2, 2L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_2);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(10), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(10), TENANT2, 3L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(10), TENANT2, 1L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(10), TENANT2, ASSIGNEE_HASH_3);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(10), TENANT1, 1L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(10), TENANT1, ASSIGNEE_HASH_3);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            new UsageMetricsFilter.Builder().withTenants(true).build());
    final var actualTU =
        usageMetricTUReader.usageMetricTUStatistics(new UsageMetricsFilter.Builder().build());

    // then
    assertThat(actualTU).isNotNull();
    final Map<String, Set<Long>> stringSetMap = actualTU.assigneesPerTenant();
    int stringSetSize = stringSetMap.values().stream().mapToInt(Set::size).sum();
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                19,
                17,
                stringSetSize,
                2,
                Map.of(
                    TENANT1,
                    new UsageMetricStatisticsEntityTenant(
                        13L, 11L, stringSetMap.get(TENANT1).size()),
                    TENANT2,
                    new UsageMetricStatisticsEntityTenant(
                        6L, 6L, stringSetMap.get(TENANT2).size()))));
  }

  @TestTemplate
  public void shouldAggregateMetricsWithoutTenant() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, ATU, now, TENANT1, 2L);
    writeTUMetric(usageMetricTUWriter, now, TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, now, TENANT1, ASSIGNEE_HASH_2);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(5), TENANT2, 3L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_2);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_3);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(new UsageMetricsFilter.Builder().build());

    final var actualTU =
        usageMetricTUReader.usageMetricTUStatistics(new UsageMetricsFilter.Builder().build());

    // then
    assertThat(actualTU).isNotNull();
    final Map<String, Set<Long>> stringSetMap = actualTU.assigneesPerTenant();
    int stringSetSize = stringSetMap.values().stream().mapToInt(Set::size).sum();
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(16, 14, stringSetSize, 2, null));
  }

  @TestTemplate
  public void shouldFilterMetricsByDate() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 1L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT1, 1L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(5), TENANT1, 2L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT1, ASSIGNEE_HASH_2);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(10), TENANT2, 1L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(10), TENANT2, 1L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(10), TENANT1, ASSIGNEE_HASH_1);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(10), TENANT1, ASSIGNEE_HASH_2);
    rdbmsWriter.flush();

    // when
    final UsageMetricsFilter umFilter =
        new Builder().startTime(now.minusMinutes(6)).endTime(now.plusMinutes(6)).build();
    final var actual = usageMetricReader.usageMetricStatistics(umFilter);
    final var actualTU = usageMetricTUReader.usageMetricTUStatistics(umFilter);

    // then
    assertThat(actualTU).isNotNull();
    final Map<String, Set<Long>> stringSetMap = actualTU.assigneesPerTenant();
    int stringSetSize = stringSetMap.values().stream().mapToInt(Set::size).sum();
    assertThat(actual).isEqualTo(new UsageMetricStatisticsEntity(1, 1, stringSetSize, 1, null));
  }

  @TestTemplate
  public void shouldFilterMetricsWithTenantByDate() {
    // given
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 1L);
    writeMetric(usageMetricWriter, RPI, now, TENANT2, 1L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 1L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(5), TENANT2, 1L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(5), TENANT2, ASSIGNEE_HASH_1);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(10), TENANT2, 1L);
    writeMetric(usageMetricWriter, ATU, now.minusMinutes(10), TENANT2, 1L);
    writeTUMetric(usageMetricTUWriter, now.minusMinutes(10), TENANT1, ASSIGNEE_HASH_2);
    rdbmsWriter.flush();

    // when
    final var actual =
        usageMetricReader.usageMetricStatistics(
            new UsageMetricsFilter.Builder()
                .startTime(now.minusMinutes(6))
                .endTime(now.plusMinutes(6))
                .withTenants(true)
                .build());
    final var actualTU =
        usageMetricTUReader.usageMetricTUStatistics(
            new Builder().startTime(now.minusMinutes(6)).endTime(now.plusMinutes(6)).build());

    // then
    assertThat(actualTU).isNotNull();
    final Map<String, Set<Long>> stringSetMap = actualTU.assigneesPerTenant();
    int stringSetSize = stringSetMap.values().stream().mapToInt(Set::size).sum();
    // then
    assertThat(actual)
        .isEqualTo(
            new UsageMetricStatisticsEntity(
                2,
                1,
                stringSetSize,
                2,
                Map.of(
                    TENANT1, new UsageMetricStatisticsEntityTenant(1L, 0L, 0L),
                    TENANT2, new UsageMetricStatisticsEntityTenant(1L, 1L, 1L))));
  }
}
