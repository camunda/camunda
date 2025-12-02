/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableResolverIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ResolveClusterVariableTest {
  @ClassRule public static final EngineRule ENGINE_RULE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void createGlobalScopedClusterVariable() {
    // given
    ENGINE_RULE
        .clusterVariables()
        .withName("KEY_1")
        .setGlobalScope()
        .withValue("\"VALUE\"")
        .create();

    // when
    final var record2 =
        ENGINE_RULE.clusterResolutionVariables().withReference("=camunda.vars.env.KEY_1").resolve();

    // then
    Assertions.assertThat(record2)
        .hasIntent(ClusterVariableResolverIntent.RESOLVED)
        .hasRecordType(RecordType.EVENT);

    final var record = record2.getValue();
    Assertions.assertThat(record).hasResolvedValue("VALUE");
  }
}
