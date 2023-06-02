/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.camunda.zeebe.protocol.record.intent.IncidentIntent.RESOLVED;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OutputMappingIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "processId";

  @Parameter public String description;

  @Parameter(1)
  public DeploymentClient deployment;

  @Parameter(2)
  public String elementId;

  @Parameter(3)
  public boolean createsJob;

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {
            "Service task",
            ENGINE
                .deployment()
                .withXmlResource(
                    Bpmn.createExecutableProcess(PROCESS_ID)
                        .startEvent()
                        .serviceTask(
                            "serviceTaskId",
                            b -> b.zeebeJobType("type").zeebeOutputExpression("foo", "bar"))
                        .endEvent()
                        .done()),
            "serviceTaskId",
            true
          },
          {
            "Intermediate throw event",
            ENGINE
                .deployment()
                .withXmlResource(
                    Bpmn.createExecutableProcess(PROCESS_ID)
                        .startEvent()
                        .intermediateThrowEvent(
                            "intermediateThrowEventId", b -> b.zeebeOutputExpression("foo", "bar"))
                        .endEvent()
                        .done()),
            "intermediateThrowEventId",
            false
          },
          {
            "Business rule task",
            ENGINE
                .deployment()
                .withXmlClasspathResource("/dmn/drg-force-user.dmn")
                .withXmlResource(
                    Bpmn.createExecutableProcess(PROCESS_ID)
                        .startEvent()
                        .businessRuleTask(
                            "businessRuleTaskId",
                            b ->
                                b.zeebeCalledDecisionId("jedi_or_sith")
                                    .zeebeResultVariable("result")
                                    .zeebeInputExpression("\"blue\"", "lightsaberColor")
                                    .zeebeOutputExpression("foo", "bar"))
                        .endEvent()
                        .done()),
            "businessRuleTaskId",
            false
          },
          {
            "None end event",
            ENGINE
                .deployment()
                .withXmlResource(
                    Bpmn.createExecutableProcess(PROCESS_ID)
                        .startEvent()
                        .endEvent("endEventId", b -> b.zeebeOutputExpression("foo", "bar"))
                        .done()),
            "endEventId",
            false
          }
        });
  }

  @Test
  public void shouldCreateIncidentOnOutputMappingFailure() {
    // given
    deployment.deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    if (createsJob) {
      ENGINE.job().withType("type").ofInstance(processInstanceKey).complete();
    }

    // then
    final var failureCommand =
        RecordingExporter.processInstanceRecords()
            .withElementId(elementId)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETING)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var incidentEvent =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(elementId)
        .hasElementInstanceKey(failureCommand.getKey())
        .hasVariableScopeKey(failureCommand.getKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
  }

  @Test
  public void shouldResolveIncidentForOutputMappingFailure() {
    // given
    deployment.deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    if (createsJob) {
      ENGINE.job().withType("type").ofInstance(processInstanceKey).complete();
    }

    final var incidentRecord =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(CREATED)
            .getFirst();

    // when
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Maps.of(entry("foo", 1))).update();
    final Record<IncidentRecordValue> resolvedIncidentRecord =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentRecord.getKey()).resolve();

    // then
    assertThat(resolvedIncidentRecord.getKey()).isEqualTo(incidentRecord.getKey());
    Assertions.assertThat(resolvedIncidentRecord.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(elementId);
    assertThat(resolvedIncidentRecord.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
    assertTrue(
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .exists());
    final Map<String, String> variables = ProcessInstances.getCurrentVariables(processInstanceKey);
    assertThat(variables).contains(entry("foo", "1"));
    assertThat(variables).contains(entry("bar", "1"));
  }

  @Test
  public void shouldResolveIncidentIfProcessCancelled() {
    deployment.deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    if (createsJob) {
      ENGINE.job().withType("type").ofInstance(processInstanceKey).complete();
    }

    final var incidentRecord =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(CREATED)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    final var resolvedIncidentRecord =
        RecordingExporter.incidentRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(RESOLVED)
            .getFirst();

    assertThat(incidentRecord.getKey()).isEqualTo(resolvedIncidentRecord.getKey());
    Assertions.assertThat(resolvedIncidentRecord.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId(PROCESS_ID)
        .hasProcessInstanceKey(processInstanceKey)
        .hasElementId(elementId);
    assertThat(resolvedIncidentRecord.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
    assertTrue(
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
            .exists());
  }
}
