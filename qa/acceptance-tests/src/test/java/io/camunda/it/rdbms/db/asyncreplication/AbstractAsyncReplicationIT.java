/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.asyncreplication;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.zeebe.test.StableValuePredicate.hasStableValue;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorageType;
import io.camunda.it.rdbms.db.util.PostgresReplicationClusterContainer;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import org.agrona.CloseHelper;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@Tag("rdbms")
@TestInstance(Lifecycle.PER_CLASS)
abstract class AbstractAsyncReplicationIT {

  protected static final Duration MAX_LAG = Duration.ofSeconds(3);

  protected final PostgresReplicationClusterContainer postgresCluster =
      new PostgresReplicationClusterContainer();

  protected TestCamundaApplication testInstance;
  protected CamundaClient camundaClient;
  protected MeterRegistry meterRegistry;

  @BeforeAll
  void beforeAll() {
    postgresCluster.start();

    testInstance =
        new TestCamundaApplication()
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withProperty("camunda.data.secondary-storage.rdbms.url", postgresCluster.getJdbcUrl())
            .withProperty(
                "camunda.data.secondary-storage.rdbms.username", postgresCluster.getUsername())
            .withProperty(
                "camunda.data.secondary-storage.rdbms.password", postgresCluster.getPassword())
            .withExporter(
                "rdbms",
                cfg -> {
                  cfg.setClassName("io.camunda.db.rdbms.exporter.RdbmsExporter");
                  cfg.setArgs(
                      Map.of(
                          "flushInterval",
                          "PT0S",
                          "asyncReplication",
                          Map.of(
                              "enabled",
                              true,
                              "pollingInterval",
                              "PT1S",
                              "maxLag",
                              MAX_LAG.toString(),
                              "pauseOnMaxLagExceeded",
                              true)));
                })
            .withBasicAuth();

    testInstance.start();
    camundaClient = testInstance.newClientBuilder().build();
    meterRegistry = testInstance.bean(MeterRegistry.class);

    Objects.requireNonNull(meterRegistry);
    Objects.requireNonNull(camundaClient);

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    exporterAcknowledgedAll();
  }

  @AfterAll
  void afterAll() {
    CloseHelper.quietClose(camundaClient);
    CloseHelper.quietClose(testInstance);
    CloseHelper.quietClose(postgresCluster);
  }

  protected void startProcessInstances(final int count) {
    for (int i = 0; i < count; i++) {
      startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    }
  }

  protected void awaitExporterPositionAdvances(final long baseline) {
    Awaitility.await()
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(() -> assertThat(getCurrentExporterPosition()).isGreaterThan(baseline));
  }

  protected void awaitAcknowledgedPositionAdvances(final long baseline) {
    Awaitility.await()
        .atMost(Duration.ofMinutes(2))
        .untilAsserted(
            () -> assertThat(getCurrentAcknowledgedExporterPosition()).isGreaterThan(baseline));
  }

  protected void awaitExporterPositionStable(final Duration stability, final Duration atMost) {
    Awaitility.await()
        .atMost(atMost)
        .during(stability)
        .until(this::getCurrentExporterPosition, hasStableValue());
  }

  protected void assertAcknowledgedPositionNotAdvancedBeyond(final long position) {
    // allow a small tolerance for in-flight confirmations at the moment of replica removal
    assertThat(getCurrentAcknowledgedExporterPosition()).isLessThanOrEqualTo(position + 5L);
  }

  protected void exporterAcknowledgedAll() {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(
            () ->
                assertThat(getCurrentExporterPosition())
                    // not all records are processed by the exporter, so we need a closeTo here
                    .isCloseTo(getCurrentAcknowledgedExporterPosition(), Offset.offset(5L)));
  }

  protected long getCurrentExporterPosition() {
    return getMeterLong(ExporterMetricsDoc.LAST_EXPORTED_POSITION.getName());
  }

  protected long getCurrentAcknowledgedExporterPosition() {
    return getMeterLong(ExporterMetricsDoc.LAST_UPDATED_EXPORTED_POSITION.getName());
  }

  private long getMeterLong(final String name) {
    final var meters =
        meterRegistry.get(name).tag("exporter", "rdbms").tag("partition", "1").meter().measure();

    final Measurement first = Iterables.getFirst(meters, null);

    if (first == null) {
      return 0;
    }

    return (long) first.getValue();
  }

  protected void wait(final Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    }
  }
}
