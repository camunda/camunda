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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class RpaReexportMigrationMultiPartitionTest {

  private static final String TEST_RPA_RESOURCE = "/resource/test-rpa-1.rpa";
  private static final int PARTITION_COUNT = 2;

  @Rule public final EngineRule engine = EngineRule.multiplePartition(PARTITION_COUNT);
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldOnlyRunMigrationOnDeploymentPartition() {
    // given - deploy an RPA resource (lands on deployment partition)
    engine.deployment().withJsonClasspathResource(TEST_RPA_RESOURCE).deploy();

    // when - enable migration and restart
    engine.stop();
    engine.withEngineConfig(cfg -> cfg.setEnableRpaReexportMigration(true));
    engine.start();

    // then - migration finishes on partition 1
    RecordingExporter.resourceReexportRecords(ResourceReexportIntent.FINISHED)
        .withPartitionId(Protocol.DEPLOYMENT_PARTITION)
        .getFirst();

    // verify all RESOURCE_REEXPORT records are only on the deployment partition
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.RESOURCE_REEXPORT)
                .limit(r -> r.getIntent() == ResourceReexportIntent.FINISHED)
                .asList())
        .extracting(r -> r.getPartitionId())
        .containsOnly(Protocol.DEPLOYMENT_PARTITION);

    // verify no RESOURCE_REEXPORT records on partition 2
    assertThat(
            RecordingExporter.records()
                .withValueType(ValueType.RESOURCE_REEXPORT)
                .limit(r -> r.getIntent() == ResourceReexportIntent.FINISHED)
                .withPartitionId(PARTITION_COUNT)
                .asList())
        .isEmpty();
  }
}
