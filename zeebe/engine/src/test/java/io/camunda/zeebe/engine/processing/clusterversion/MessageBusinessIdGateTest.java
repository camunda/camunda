/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that {@link Capability#MESSAGE_BUSINESS_ID_CORRELATION} gates the businessId-uniqueness
 * feature (issue #51689) on top of the operator-set {@code businessIdUniquenessEnabled} config.
 *
 * <p>The feature activates only when BOTH conditions hold:
 *
 * <ul>
 *   <li>config flag enabled (operator opt-in)
 *   <li>ECV raised to {@code MESSAGE_BUSINESS_ID_CORRELATION} (rolling-upgrade safety)
 * </ul>
 *
 * <p>If only the config flag is on but ECV stays at BASELINE, the engine takes the legacy path —
 * businessId carried on records but never enforced. This protects rolling-upgrade safety: a leader
 * with the config flag on cannot emit the new cross-partition handshake intents to the log until
 * every replica supports them. The complementary case (config on + ECV raised → enforcement
 * activates) is covered by {@code CreateProcessInstanceBusinessIdUniquenessToggleTest}.
 */
public final class MessageBusinessIdGateTest {

  private static final String PROCESS_ID = "process";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setBusinessIdUniquenessEnabled(true));

  // intentionally NOT calling withInitialClusterVersionAtMax() — the ECV gate stays closed,
  // so the combined runtime flag in MessageEventProcessors resolves to false

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotEnforceUniquenessWhenEcvBelowGate() {
    // given — config flag is ON but ECV is at BASELINE (below MESSAGE_BUSINESS_ID_CORRELATION).
    // Combined effective gate is OFF.
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done())
        .deploy();

    // when — two process instance creates carry the same businessId. With both gates open this
    // would reject the second; with the ECV gate closed the engine takes the legacy path and
    // accepts both.
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withBusinessId("biz-1")
        .withTags("first")
        .create();
    engine
        .processInstance()
        .ofBpmnProcessId(PROCESS_ID)
        .withBusinessId("biz-1")
        .withTags("duplicate")
        .create();

    // then — both CREATED records appear. If the gate were open and uniqueness enforced, only
    // the first would be CREATED and the second would be REJECTED, so this count check
    // exclusively confirms the gate suppressed enforcement.
    final long acceptedCreates =
        RecordingExporter.processInstanceCreationRecords()
            .withIntent(ProcessInstanceCreationIntent.CREATED)
            .filter(r -> "biz-1".equals(r.getValue().getBusinessId()))
            .limit(2)
            .count();
    assertThat(acceptedCreates)
        .as("both duplicate creates accepted while ECV gate is closed")
        .isEqualTo(2L);
  }
}
