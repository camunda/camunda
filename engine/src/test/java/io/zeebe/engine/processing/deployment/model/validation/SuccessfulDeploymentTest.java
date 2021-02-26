/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public final class SuccessfulDeploymentTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployWorkflowWithOrphanErrorDefinition() {
    // when
    final var deployment =
        engine
            .deployment()
            .withXmlClasspathResource("/workflows/orphan-error-definition.bpmn")
            .deploy();

    // then
    assertThat(deployment.getIntent()).isEqualTo(DeploymentIntent.CREATED);
    assertThat(deployment.getValue().getDeployedWorkflows()).hasSize(1);
  }
}
