/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterFactory;
import io.camunda.zeebe.broker.exporter.util.ControlledTestExporter;
import io.camunda.zeebe.db.layered.zdb.LayeredZeebeDbConfig;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;

/**
 * The exporter ownership domain (experimental layered-state flag): exporter positions are written
 * through the {@code exporter} domain's context — buffered in memory on the director's actor — and
 * drained to committed RocksDB by the director's own persist cadence; a successor director on the
 * same database (a leader transition) takes the domain over and keeps working.
 *
 * <p>Assertions on committed positions read through a pass-through context ({@link
 * ExporterRule#getExportersState()}), i.e. they only see what a persist round drained — exactly
 * what snapshot selection and log compaction see.
 */
public final class ExporterDirectorLayeredStateTest {

  private static final String EXPORTER_ID = "layered-test-exporter";
  private static final Duration PERSIST_INTERVAL = Duration.ofMillis(100);
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final LayeredZeebeDbConfig layeredConfig =
      new LayeredZeebeDbConfig(
          LayeredZeebeDbConfig.defaults().maxBytesPerStore(),
          LayeredZeebeDbConfig.defaults().absorbDeletes(),
          LayeredZeebeDbConfig.defaults().pipelineSegmentLimit(),
          PERSIST_INTERVAL,
          LayeredZeebeDbConfig.defaults().freezeInterval());

  @Rule public final ExporterRule rule = ExporterRule.activeExporter().withLayeredDb(layeredConfig);

  private final ControlledTestExporter exporter =
      new ControlledTestExporter().shouldAutoUpdatePosition(true);

  @Test
  public void shouldPersistBufferedExporterPositionsAtCadence() {
    // given a director writing exporter positions through the exporter domain
    rule.startExporterDirector(List.of(descriptor()));

    // the initial exporter entry is drained right away, so snapshot selection (which reads
    // committed RocksDB only) sees the exporter and keeps bounding compaction by its position
    Awaitility.await("until the initial exporter entry is committed")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(rule.getExportersState().hasExporters()).isTrue());

    // when a record is exported and acknowledged
    final long position = rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());

    // then the buffered position is visible to the director immediately and reaches committed
    // RocksDB within the persist cadence
    Awaitility.await("until the director observes the acknowledged position")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> assertThat(rule.getDirector().getLowestPosition().join()).isEqualTo(position));
    Awaitility.await("until a persist round committed the position")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(rule.getExportersState().getPosition(EXPORTER_ID)).isEqualTo(position));
  }

  @Test
  public void shouldTakeOverExporterDomainOnSameDbAfterRestart() {
    // given a director whose acknowledged position was persisted
    rule.startExporterDirector(List.of(descriptor()));
    final long firstPosition = rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await("until a persist round committed the first position")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(rule.getExportersState().getPosition(EXPORTER_ID))
                    .isEqualTo(firstPosition));

    // when a successor director takes over the same database (a leader transition)
    rule.stopExporterDirector();
    rule.startExporterDirectorOnSameDb(List.of(descriptor()));

    // then it recovered the persisted position and keeps exporting and persisting through the
    // taken-over domain
    final long secondPosition = rule.writeEvent(DeploymentIntent.CREATED, new DeploymentRecord());
    Awaitility.await("until the successor's persist round committed the new position")
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(rule.getExportersState().getPosition(EXPORTER_ID))
                    .isEqualTo(secondPosition));
    assertThat(secondPosition).isGreaterThan(firstPosition);
  }

  private ExporterDescriptor descriptor() {
    return new ExporterDescriptor(
        EXPORTER_ID,
        new ExporterFactory() {
          @Override
          public String exporterId() {
            return EXPORTER_ID;
          }

          @Override
          public Exporter newInstance() {
            return exporter;
          }

          @Override
          public boolean isSameTypeAs(final ExporterFactory other) {
            return other != null && EXPORTER_ID.equals(other.exporterId());
          }
        },
        Map.of());
  }
}
