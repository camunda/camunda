/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization test for the {@code propagateOnlyMappedNestedOutputPaths} kill-switch: with the
 * flag disabled, a nested output mapping target propagates the completing element's whole variable
 * again, including branches no mapping ever targeted (the pre-#35251 behavior).
 */
public final class NestedOutputPathPropagationFeatureFlagTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition()
          .withFeatureFlags(f -> f.setPropagateOnlyMappedNestedOutputPaths(false));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldPropagateUnmappedLocalBranchWhenDisabled() {
    // given: the same model as NestedOutputPathPropagationTest's discriminator
    final var processId = "nested-output-flag-off";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("={p:0}", "a")
                        .zeebeOutputExpression("1", "a.b")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{\"a\":{\"c\":2}}")
            .create();

    // then: the element-local 'p' is merged into the parent again
    final var records =
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords()
            .withScopeKey(processInstanceKey)
            .withName("a")
            .asList();
    assertThat(records).isNotEmpty();
    JsonUtil.assertEquality(records.getLast().getValue().getValue(), "{\"p\":0,\"b\":1}");
  }
}
