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
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Iterables;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.zeebe.broker.exporter.stream.ExporterMetricsDoc;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import org.assertj.core.data.Offset;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Integration test for {@link io.camunda.exporter.rdbms.replication.DelayReplicationController}.
 *
 * <p>Verifies that exported positions are withheld from acknowledgment until the configured delay
 * elapses, and that they are acknowledged afterwards. Uses H2 in-memory database (vendor-agnostic)
 * — no replication cluster is required.
 *
 * <p>Tagged {@code dl-nightly} because the 1-minute delay makes it too slow for regular CI.
 */
@Tag("dl-nightly")
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class DelayReplicationIT {

  /**
   * Minimum delay that keeps the "not yet acknowledged" assertion safe even on slow CI runners.
   * Must be long enough that the assertion in {@code shouldNotAcknowledgeBeforeDelayExpires}
   * completes well before the first {@code checkDue} fires.
   */
  private static final Duration REPLICATION_DELAY = Duration.ofMinutes(1);

  @AutoClose TestCamundaApplication testInstance;
  @AutoClose CamundaClient camundaClient;
  MeterRegistry meterRegistry;

  @BeforeAll
  void beforeAll() {
    testInstance =
        new TestCamundaApplication()
            .withSecondaryStorageType(SecondaryStorageType.rdbms)
            .withProperty(
                "camunda.data.secondary-storage.rdbms.url",
                "jdbc:h2:mem:delay-replication-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
            .withProperty("camunda.data.secondary-storage.rdbms.username", "sa")
            .withProperty("camunda.data.secondary-storage.rdbms.password", "")
            .withProperty("camunda.data.secondary-storage.rdbms.flush-interval", "PT0.5S")
            .withProperty("camunda.data.secondary-storage.rdbms.async-replication.enabled", "true")
            .withProperty("camunda.data.secondary-storage.rdbms.async-replication.type", "DELAY")
            .withProperty(
                "camunda.data.secondary-storage.rdbms.async-replication.delay",
                REPLICATION_DELAY.toString())
            .withProperty(
                "camunda.data.secondary-storage.rdbms.async-replication.queue-debounce-time",
                Duration.ZERO.toString())
            .withBasicAuth();

    testInstance.start();
    camundaClient = testInstance.newClientBuilder().build();
    meterRegistry = testInstance.bean(MeterRegistry.class);

    Objects.requireNonNull(meterRegistry);
    Objects.requireNonNull(camundaClient);

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    // drain any startup traffic before recording baselines
    exporterAcknowledgedAll();
  }

  @Test
  @Order(1)
  void shouldNotAcknowledgeBeforeDelayExpires() {
    // given - snapshot positions before generating new traffic
    final long baselineAcknowledgedPosition = getCurrentAcknowledgedExporterPosition();

    // when - create export traffic
    for (int i = 0; i < 5; i++) {
      startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}");
    }

    // wait for the exporter to flush the new records to secondary storage
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(getCurrentExporterPosition())
                    .isGreaterThan(baselineAcknowledgedPosition));

    Awaitility.await()
        .during(Duration.ofSeconds(30))
        .atMost(Duration.ofSeconds(35))
        .untilAsserted(
            () ->
                assertThat(getCurrentAcknowledgedExporterPosition())
                    .isEqualTo(baselineAcknowledgedPosition));

    // then - the delay has not elapsed yet: acknowledged position must not have advanced
    // (REPLICATION_DELAY is PT1M; this assertion runs within a few seconds of the flush)
    assertThat(getCurrentAcknowledgedExporterPosition())
        .isLessThanOrEqualTo(baselineAcknowledgedPosition);
  }

  @Test
  @Order(2)
  void shouldAcknowledgeAfterDelayExpires() {
    // given - unacknowledged positions remain from @Order(1)
    final long acknowledgedBefore = getCurrentAcknowledgedExporterPosition();

    // then - after the configured delay elapses, positions are eventually acknowledged
    // Allow 2x the delay plus buffer to account for CI jitter and rescheduling lag
    Awaitility.await()
        .atMost(REPLICATION_DELAY.multipliedBy(2).plus(Duration.ofSeconds(30)))
        .untilAsserted(
            () ->
                assertThat(getCurrentAcknowledgedExporterPosition())
                    .isGreaterThan(acknowledgedBefore));

    // and the exporter fully catches up
    exporterAcknowledgedAll();
  }

  private void exporterAcknowledgedAll() {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(REPLICATION_DELAY.plus(Duration.ofSeconds(30)))
        .untilAsserted(
            () ->
                assertThat(getCurrentExporterPosition())
                    .isCloseTo(getCurrentAcknowledgedExporterPosition(), Offset.offset(5L)));
  }

  private long getCurrentExporterPosition() {
    return getMeterLong(ExporterMetricsDoc.LAST_EXPORTED_POSITION.getName());
  }

  private long getCurrentAcknowledgedExporterPosition() {
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
}
