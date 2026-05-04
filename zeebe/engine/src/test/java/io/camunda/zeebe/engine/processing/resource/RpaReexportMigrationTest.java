/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RpaReexportMigrationTest {

  private static final String TEST_RPA_RESOURCE = "/resource/test-rpa-1.rpa";
  private static final String TEST_GENERIC_RESOURCE = "/resource/test-generic-1.txt";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRunMigrationLifecycleAndReexportRpaResources() {
    // given - an RPA resource deployed before migration runs (migration disabled by default)
    engine.deployment().withJsonClasspathResource(TEST_RPA_RESOURCE).deploy();
    final var createdRecord =
        RecordingExporter.resourceRecords().withIntent(ResourceIntent.CREATED).getFirst();

    // when - enable migration and restart the engine
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableRpaReexportMigration(true));
    engine.start();

    // then - migration completes with FINISHED event
    RecordingExporter.resourceReexportRecords(ResourceReexportIntent.FINISHED).getFirst();

    // verify the full expected intent lifecycle
    final var reexportIntents =
        RecordingExporter.resourceReexportRecords()
            .limit(r -> r.getIntent() == ResourceReexportIntent.FINISHED)
            .asList()
            .stream()
            .map(Record::getIntent)
            .toList();
    assertThat(reexportIntents)
        .containsExactly(
            ResourceReexportIntent.START,
            ResourceReexportIntent.STARTED,
            ResourceReexportIntent.REEXPORT,
            ResourceReexportIntent.REEXPORT,
            ResourceReexportIntent.FINISHED);

    // verify the REEXPORTED event matches the original CREATED event
    final var reexportedRecord =
        RecordingExporter.resourceRecords().withIntent(ResourceIntent.REEXPORTED).getFirst();
    final Resource reexported = reexportedRecord.getValue();
    final Resource created = createdRecord.getValue();
    assertThat(reexported.getResourceKey()).isEqualTo(created.getResourceKey());
    assertThat(reexported.getResourceId()).isEqualTo(created.getResourceId());
    assertThat(reexported.getResourceName()).isEqualTo(created.getResourceName());
    assertThat(reexported.getVersion()).isEqualTo(created.getVersion());
    assertThat(reexported.getTenantId()).isEqualTo(created.getTenantId());
    assertThat(reexported.getDeploymentKey()).isEqualTo(created.getDeploymentKey());
    assertThat(reexported.getChecksum()).isEqualTo(created.getChecksum());
  }

  @Test
  public void shouldNotReexportNonRpaResources() {
    // given - only a non-RPA resource is deployed
    engine.deployment().withJsonClasspathResource(TEST_GENERIC_RESOURCE).deploy();

    // when - enable migration and restart
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableRpaReexportMigration(true));
    engine.start();

    // then - migration finishes without any REEXPORTED event
    RecordingExporter.resourceReexportRecords(ResourceReexportIntent.FINISHED).getFirst();

    // use the FINISHED record as a boundary so the assertion doesn't block
    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getValueType() == ValueType.RESOURCE_REEXPORT
                            && r.getIntent() == ResourceReexportIntent.FINISHED)
                .withValueType(ValueType.RESOURCE)
                .withIntent(ResourceIntent.REEXPORTED)
                .asList())
        .isEmpty();
  }

  @Test
  public void shouldNotRunMigrationAgainAfterRestart() {
    // given - deploy an RPA resource and run the migration once
    engine.deployment().withJsonClasspathResource(TEST_RPA_RESOURCE).deploy();

    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableRpaReexportMigration(true));
    engine.start();

    final var finishedRecord =
        RecordingExporter.resourceReexportRecords(ResourceReexportIntent.FINISHED).getFirst();

    // when - restart the engine again (migration already ran)
    engine.stop();
    engine.start();

    // trigger a marker deployment so we know the engine is running past the migration window
    final var markerDeploymentKey =
        engine.deployment().withJsonClasspathResource(TEST_RPA_RESOURCE).deploy().getKey();
    final var markerRecord =
        RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
            .filter(r -> r.getKey() == markerDeploymentKey)
            .getFirst();

    // then - no new RESOURCE_REEXPORT records were written after the FINISHED event
    // We use limit(markerPos) to bound the stream, then post-filter by position to exclude
    // replayed records (which have the same positions as the original migration records).
    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getPosition() >= markerRecord.getPosition())
                .withValueType(ValueType.RESOURCE_REEXPORT)
                .asList()
                .stream()
                .filter(r -> r.getPosition() > finishedRecord.getPosition())
                .toList())
        .isEmpty();
  }
}
