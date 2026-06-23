/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class InputMappingNestingDepthIncidentTest {

  private static final int MAX_DEPTH = 3;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withEngineConfig(c -> c.setMaxVariableNestingDepth(MAX_DEPTH));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private long processDefinitionKey;
  private String processId;

  @Before
  public void init() {
    processId = helper.getBpmnProcessId();
    processDefinitionKey =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "task",
                        t ->
                            t.zeebeJobType(helper.getJobType())
                                .zeebeInputExpression("{a: {b: {c: \"leaf\"}}}", "nested"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();
  }

  @Test
  public void shouldCreateIncidentWhenInputMappingExceedsNestingDepth() {
    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var incident =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incident.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId(processId)
        .hasProcessDefinitionKey(processDefinitionKey)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId("task")
        .hasTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    assertThat(incident.getValue().getErrorMessage())
        .contains(
            "Expected variable document to be nested at most %d levels deep, but it exceeds that limit"
                .formatted(MAX_DEPTH));
  }
}
