/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Tests for incidents specific for business-rule-tasks. */
public class BusinessRuleTaskIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ELEMENT_ID = "business-rule-task";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance processWithBusinessRuleTask(
      final Consumer<BusinessRuleTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .businessRuleTask(TASK_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  private IncidentRecordValueAssert assertIncidentCreated(
      final long processInstanceKey, final long elementInstanceKey) {
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return Assertions.assertThat(incidentRecord.getValue())
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(elementInstanceKey)
        .hasJobKey(-1L)
        .hasVariableScopeKey(elementInstanceKey);
  }

  // --------------------------------------------------------------------------
  // ----- CalledDecision related tests

  @Test
  public void shouldCreateIncidentIfDecisionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId("jedi-or-sith").zeebeResultVariable("result")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.CALLED_ELEMENT_ERROR)
        .hasErrorMessage(
            """
            Expected to evaluate decision 'jedi-or-sith', \
            but failed to evaluate expression 'lightsaberColor': \
            no variable found for name 'lightsaberColor'\
            """);
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFails() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId("jedi-or-sith").zeebeResultVariable("result")))
        .deploy();

    // and an instance of that process is created without the required variables for the decision
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incidentCreated.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentAfterDecisionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId("jedi-or-sith").zeebeResultVariable("result")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("lightsaberColor", "blue")))
        .update();

    // ... resolve incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .incidentRecords()
                .onlyEvents())
        .extracting(Record::getKey, Record::getIntent)
        .describedAs("created incident is resolved and no new incident is created")
        .containsExactly(
            tuple(incidentCreated.getKey(), IncidentIntent.CREATED),
            tuple(incidentCreated.getKey(), IncidentIntent.RESOLVED));

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .describedAs("business rule task is successfully completed")
        .isTrue();
  }
}
