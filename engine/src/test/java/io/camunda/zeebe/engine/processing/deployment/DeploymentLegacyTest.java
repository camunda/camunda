/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static io.camunda.zeebe.protocol.record.JsonSerializableAssert.assertThat;

import io.camunda.zeebe.engine.state.immutable.DeploymentState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class DeploymentLegacyTest {

  public static final long DEPLOYMENT_KEY = 123L;
  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldStoreRawDeploymentInStateForEventsWrittenBefore8dot2() {
    // when
    writeDeploymentForVersion("8.1.0");

    // then
    final DeploymentState deploymentState = engine.getProcessingState().getDeploymentState();
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(DEPLOYMENT_KEY);
    assertThat(storedDeploymentRecord).isNotNull();
  }

  @Test
  public void shouldNotStoreRawDeploymentInStateForEventsWrittenAfter8dot2() {
    // when
    writeDeploymentForVersion("8.2.0");

    // then
    final DeploymentState deploymentState = engine.getProcessingState().getDeploymentState();
    final var storedDeploymentRecord = deploymentState.getStoredDeploymentRecord(DEPLOYMENT_KEY);
    assertThat(storedDeploymentRecord).isNull();
  }

  private void writeDeploymentForVersion(final String brokerVersion) {
    engine.stop();
    final var deploymentRecord = new DeploymentRecord();
    engine.writeRecords(
        RecordToWrite.command()
            .key(DEPLOYMENT_KEY)
            .deployment(deploymentRecord, DeploymentIntent.CREATE),
        RecordToWrite.event()
            .key(DEPLOYMENT_KEY)
            .brokerVersion(VersionInfo.parse(brokerVersion))
            .deployment(deploymentRecord, DeploymentIntent.CREATED)
            .causedBy(0));
    engine.start();
  }
}
