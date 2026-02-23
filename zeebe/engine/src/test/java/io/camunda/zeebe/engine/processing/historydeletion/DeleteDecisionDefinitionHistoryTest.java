/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.historydeletion;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class DeleteDecisionDefinitionHistoryTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String DMN_RESOURCE = "/dmn/drg-force-user.dmn";

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDeleteDecisionDefinitionHistory() {
    // given
    final var decisionRequirementsKey =
        ENGINE
            .deployment()
            .withXmlClasspathResource(DMN_RESOURCE)
            .deploy()
            .getValue()
            .getDecisionsMetadata()
            .getFirst()
            .getDecisionRequirementsKey();

    // when
    final var deletedEvent =
        ENGINE.historyDeletion().decisionRequirements(decisionRequirementsKey).delete();

    // then
    assertThat(deletedEvent.getValue())
        .hasResourceKey(decisionRequirementsKey)
        .hasResourceType(HistoryDeletionType.DECISION_REQUIREMENTS);
  }
}
