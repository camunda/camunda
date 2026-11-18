/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordSizeBaseline;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/** Checks the log bytes of commands that the engine distributes. See {@link RecordSizeBaseline}. */
public final class DistributionRecordSizeBaselineTest {

  @Rule public final EngineRule engine = EngineRule.multiplePartition(3);

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotChangeRecordSizesOfDeploymentDistribution() {
    // given
    final var startPositions = RecordSizeBaseline.lastPositions(engine);

    // when
    final long deploymentKey =
        engine
            .deployment()
            .withXmlResource(Bpmn.createExecutableProcess("process").startEvent().endEvent().done())
            .deploy(RecordSizeBaseline.oidcClaims("demo"))
            .getKey();
    RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
        .withDistributionIntent(DeploymentIntent.CREATE)
        .withRecordKey(deploymentKey)
        .await();

    // then
    RecordSizeBaseline.assertMatches(engine, "deployment-distribution", startPositions);
  }
}
