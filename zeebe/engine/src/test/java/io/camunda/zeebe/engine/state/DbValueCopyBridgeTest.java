/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.LatestProcessVersion;
import io.camunda.zeebe.engine.state.deployment.VersionInfo;
import io.camunda.zeebe.engine.state.jobmetrics.MetricsValue;
import io.camunda.zeebe.engine.state.metrics.PersistedUsageMetrics;
import io.camunda.zeebe.engine.state.user.PersistedUser;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DbValueCopyBridgeTest {

  @Test
  void shouldCopyLatestProcessVersionViaExplicitDbValueImplementation() {
    // given
    final var source = new LatestProcessVersion();
    source.set(42L);

    // when
    final var target = (LatestProcessVersion) source.newInstance();
    ((io.camunda.zeebe.db.DbValue) source).copyTo(target);

    // then
    assertThat(target.get()).isEqualTo(42L);

    source.set(99L);
    assertThat(target.get()).isEqualTo(42L);
  }

  @Test
  void shouldCopyVersionInfoViaExplicitDbValueImplementation() {
    // given
    final var source = new VersionInfo();
    source.addKnownVersion(1L);
    source.addKnownVersion(3L);
    source.addKnownVersion(2L);

    // when
    final var target = (VersionInfo) source.newInstance();
    ((io.camunda.zeebe.db.DbValue) source).copyTo(target);

    // then
    assertThat(target.getHighestVersion()).isEqualTo(3L);
    assertThat(target.getKnownVersions()).containsExactly(1L, 2L, 3L);

    source.addKnownVersion(4L);
    assertThat(target.getKnownVersions()).containsExactly(1L, 2L, 3L);
  }

  @Test
  void shouldCopyPersistedUserViaExplicitDbValueImplementation() {
    // given
    final var record =
        new UserRecord()
            .setUserKey(123L)
            .setUsername("demo")
            .setName("Demo User")
            .setEmail("demo@example.com")
            .setPassword("secret");
    final var source = new PersistedUser();
    source.setUser(record);

    // when
    final var target = (PersistedUser) source.newInstance();
    ((io.camunda.zeebe.db.DbValue) source).copyTo(target);

    // then
    assertThat(target.getUserKey()).isEqualTo(123L);
    assertThat(target.getUsername()).isEqualTo("demo");
    assertThat(target.getName()).isEqualTo("Demo User");
    assertThat(target.getEmail()).isEqualTo("demo@example.com");
    assertThat(target.getPassword()).isEqualTo("secret");

    source.getUser().setUsername("changed");
    assertThat(target.getUsername()).isEqualTo("demo");
  }

  @Test
  void shouldCopyMetricsValueViaDirectOverride() {
    // given
    final var source = new MetricsValue();
    source.incrementMetric(JobMetricsExportState.CREATED, 10L);
    source.incrementMetric(JobMetricsExportState.CREATED, 20L);
    source.incrementMetric(JobMetricsExportState.COMPLETED, 30L);

    // when
    final var target = source.newInstance();
    source.copyTo(target);

    // then
    assertThat(target.getMetricForStatus(JobMetricsExportState.CREATED).getCount()).isEqualTo(2);
    assertThat(target.getMetricForStatus(JobMetricsExportState.CREATED).getLastUpdatedAt())
        .isEqualTo(20L);
    assertThat(target.getMetricForStatus(JobMetricsExportState.COMPLETED).getCount()).isEqualTo(1);
    assertThat(target.getMetricForStatus(JobMetricsExportState.COMPLETED).getLastUpdatedAt())
        .isEqualTo(30L);

    source.incrementMetric(JobMetricsExportState.CREATED, 40L);
    assertThat(target.getMetricForStatus(JobMetricsExportState.CREATED).getCount()).isEqualTo(2);
  }

  @Test
  void shouldCopyPersistedUsageMetricsViaExplicitDbValueImplementation() {
    // given
    final var source =
        new PersistedUsageMetrics()
            .setFromTime(10L)
            .setToTime(20L)
            .setTenantRPIMap(Map.of("t1", 2L))
            .setTenantEDIMap(Map.of("t1", 3L))
            .setTenantTUMap(Map.of("t1", Set.of(11L, 12L)));

    // when
    final var target = (PersistedUsageMetrics) source.newInstance();
    ((io.camunda.zeebe.db.DbValue) source).copyTo(target);

    // then
    assertThat(target.getFromTime()).isEqualTo(10L);
    assertThat(target.getToTime()).isEqualTo(20L);
    assertThat(target.getTenantRPIMap()).containsEntry("t1", 2L);
    assertThat(target.getTenantEDIMap()).containsEntry("t1", 3L);
    assertThat(target.getTenantTUMap()).containsEntry("t1", Set.of(11L, 12L));

    source.setFromTime(99L);
    assertThat(target.getFromTime()).isEqualTo(10L);
  }
}
