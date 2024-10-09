/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExporterContextTest {
  private static final Logger LOG = LoggerFactory.getLogger(ExporterContextTest.class);
  private static final InstantSource FIXED_INSTANT_SOURCE =
      InstantSource.fixed(Instant.ofEpochMilli(123456789));

  public ExporterContext makeExporterContext(
      final int partitionId, final String exporterId, final MeterRegistry underlying) {
    return new ExporterContext(
        LOG,
        new ExporterTestConfiguration<>(exporterId, Collections.emptyMap()),
        partitionId,
        underlying,
        FIXED_INSTANT_SOURCE);
  }

  @Test
  void shouldAddTagsAndCleanupWhenClosed() {
    // given
    final var partitionId = 1;
    final var exporterId = "MetricTestExporter";
    final var underlyingMeterRegistry = new SimpleMeterRegistry();
    final var context = makeExporterContext(partitionId, exporterId, underlyingMeterRegistry);
    final var meterRegistry = context.getMeterRegistry();
    final var allRegistries = List.of(meterRegistry, underlyingMeterRegistry);

    assertThat(meterRegistry.getMeters().size())
        .isEqualTo(0)
        .describedAs("Expected no metrics to be measured at start");

    // when
    final var timer = meterRegistry.timer("test");
    timer.record(11, TimeUnit.MILLISECONDS);

    final var expectedTags = Tags.of("partition", "1", "exporterId", exporterId);

    // then
    allRegistries.forEach(
        mr ->
            assertThat(mr.timer("test", expectedTags).count())
                .isEqualTo(1)
                .describedAs("Expected exactly 1 observed test sample counted"));

    // when
    context.close();

    // then
    allRegistries.forEach(
        mr ->
            assertThat(mr.timer("test", expectedTags).count())
                .isEqualTo(0)
                .describedAs(
                    "Metrics should be removed from the registry when exporter is closed"));
  }
}
