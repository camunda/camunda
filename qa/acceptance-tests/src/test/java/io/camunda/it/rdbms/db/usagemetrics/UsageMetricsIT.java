/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.usagemetrics;

import static io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType.EDI;
import static io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType.RPI;
import static org.assertj.core.api.Assertions.*;

import io.camunda.db.rdbms.write.domain.UsageMetricDbModel;
import io.camunda.db.rdbms.write.service.UsageMetricWriter;
import io.camunda.it.rdbms.db.util.CamundaRdbmsInvocationContextProviderExtension;
import io.camunda.it.rdbms.db.util.CamundaRdbmsTestApplication;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricStatisticsEntity.UsageMetricStatisticsEntityTenant;
import io.camunda.search.filter.UsageMetricsFilter;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import java.time.OffsetDateTime;
import java.util.Map;
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

  private void writeMetric(
      final UsageMetricWriter usageMetricWriter,
      final EventType eventType,
      final OffsetDateTime time,
      final String tenantId,
      final long value) {
    usageMetricWriter.create(
        new UsageMetricDbModel.Builder()
            .startTime(time.minusMinutes(5))
            .endTime(time)
            .tenantId(tenantId)
            .eventType(eventType)
            .value(value)
            .partitionId(PARTITION_ID.intValue())
            .historyCleanupDate(null)
            .build());
  }

  @TestTemplate
  public void shouldCreateSequenceFlow(final CamundaRdbmsTestApplication testApplication) {
    // given
    final var rdbmsService = testApplication.getRdbmsService();
    final var rdbmsWriter = rdbmsService.createWriter(PARTITION_ID);
    final var usageMetricReader = rdbmsService.getUsageMetricReader();
    final var usageMetricWriter = rdbmsWriter.getUsageMetricWriter();

    // when
    final var now = OffsetDateTime.now();
    writeMetric(usageMetricWriter, RPI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, EDI, now, TENANT1, 11L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT1, 2L);
    writeMetric(usageMetricWriter, RPI, now.minusMinutes(5), TENANT2, 3L);
    writeMetric(usageMetricWriter, EDI, now.minusMinutes(5), TENANT2, 3L);
    rdbmsWriter.flush();

    // then
    final var actual =
        usageMetricReader.usageMetricStatistics(new UsageMetricsFilter.Builder().build());
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
}
