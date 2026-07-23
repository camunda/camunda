/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.broker.exporter.metrics.MetricsExporter;
import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration test proving that Zeebe's exporters run independently of each other: when one
 * exporter's backing store becomes unavailable, every other configured exporter keeps exporting new
 * records, unaffected and without delay.
 *
 * <p>Pairs a real {@code io.camunda.db.rdbms.exporter.RdbmsExporter} (backed by a stoppable
 * Postgres testcontainer) with the built-in {@link MetricsExporter} (no external dependency, always
 * succeeds). Both run on the same partition, through the same actor scheduler, as in production.
 * Stopping the Postgres container simulates a database outage for the RDBMS exporter only; {@link
 * MetricsExporter}'s position must keep advancing throughout, proving the outage does not block or
 * slow down the other exporter. Two independent {@code RdbmsExporter} instances against two
 * separate databases are not possible in this architecture: the RDBMS exporter's connection is
 * always sourced from the single, app-wide secondary-storage datasource, not from its own exporter
 * arguments.
 */
@Tag("rdbms")
@TestInstance(Lifecycle.PER_CLASS)
class ExporterFailureIsolationIT {

  private static final String POSTGRES_IMAGE = "postgres:16-alpine";
  private static final String DATABASE_NAME = "camunda";
  private static final String DATABASE_USER = "camunda";
  private static final String DATABASE_PASSWORD = "camunda";
  private static final String RDBMS_EXPORTER_ID = "rdbms";

  @AutoClose
  private final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(POSTGRES_IMAGE)
          .withDatabaseName(DATABASE_NAME)
          .withUsername(DATABASE_USER)
          .withPassword(DATABASE_PASSWORD)
          .withStartupTimeout(Duration.ofMinutes(5));

  @AutoClose private TestCamundaApplication testInstance;
  @AutoClose private CamundaClient camundaClient;

  private MeterRegistry meterRegistry;

  @BeforeAll
  void beforeAll() {
    postgres.start();

    testInstance =
        new TestCamundaApplication()
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withProperty("camunda.monitoring.metrics.enable-exporter-execution-metrics", true)
            .withProperty("camunda.data.secondary-storage.rdbms.url", postgres.getJdbcUrl())
            .withProperty("camunda.data.secondary-storage.rdbms.username", postgres.getUsername())
            .withProperty("camunda.data.secondary-storage.rdbms.password", postgres.getPassword())
            .withExporter(
                RDBMS_EXPORTER_ID,
                cfg -> {
                  cfg.setClassName("io.camunda.db.rdbms.exporter.RdbmsExporter");
                  cfg.setArgs(Map.of("flushInterval", "PT0S"));
                })
            .withBasicAuth();

    testInstance.start();

    camundaClient = testInstance.newClientBuilder().build();
    meterRegistry = testInstance.bean(MeterRegistry.class);

    Objects.requireNonNull(meterRegistry);
    Objects.requireNonNull(camundaClient);

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // Establish the healthy-state case first: with the database still up, both exporters must be
    // able to advance past their initial (empty) position.
    startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    Awaitility.await("both exporters advance while healthy")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(getCurrentAcknowledgedPosition(RDBMS_EXPORTER_ID)).isGreaterThan(0);
              assertThat(getCurrentAcknowledgedPosition(MetricsExporter.defaultExporterId()))
                  .isGreaterThan(0);
            });
  }

  @Test
  void shouldContinueExportingWithHealthyExporterWhenSiblingsDatabaseIsDown() {
    // given - both exporters are healthy and caught up
    final long rdbmsPositionBeforeOutage = getCurrentAcknowledgedPosition(RDBMS_EXPORTER_ID);
    final long metricsPositionBeforeOutage =
        getCurrentAcknowledgedPosition(MetricsExporter.defaultExporterId());

    // when - the RDBMS exporter's database goes down
    postgres.stop();

    for (int i = 0; i < 10; i++) {
      startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    }

    // then - the healthy exporter keeps exporting the new records, unaffected by its sibling's
    // database outage
    Awaitility.await("MetricsExporter keeps advancing while RDBMS exporter's database is down")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(getCurrentAcknowledgedPosition(MetricsExporter.defaultExporterId()))
                    .as("MetricsExporter must keep exporting new records independently")
                    .isGreaterThan(metricsPositionBeforeOutage));

    // and - the RDBMS exporter, whose database is unreachable, has made no meaningful progress:
    // it is retrying in the background, not silently dropping or skipping records
    assertThat(getCurrentAcknowledgedPosition(RDBMS_EXPORTER_ID))
        .as("RDBMS exporter must not have advanced while its database was down")
        .isCloseTo(rdbmsPositionBeforeOutage, Offset.offset(2L));
  }

  private long getCurrentAcknowledgedPosition(final String exporterId) {
    try {
      final var meters =
          meterRegistry
              .get(ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION.getName())
              .tag("exporter", exporterId)
              .tag("partition", "1")
              .meter()
              .measure();
      final Measurement first = Iterables.getFirst(meters, null);
      return first == null ? 0L : (long) first.getValue();
    } catch (final Exception e) {
      return 0L;
    }
  }
}
