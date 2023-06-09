/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.FeatureFlags;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class StraightThroughProcessingLoopValidationDisabledTest {
  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          // Disable loop detector feature flag
          .withFeatureFlags(new FeatureFlags(true, false, true, true, false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporter = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployProcessWithRegularLoops() {
    // given
    final var processId = Strings.newRandomValidBpmnId();

    // when
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .task("task1")
                    .task("task2")
                    .connectTo("task1")
                    .done())
            .deploy();

    // then
    assertThat(deployment.getKey())
        .describedAs("Don't reject loops if FeatureFlag is disabled")
        .isNotNegative();
  }
}
