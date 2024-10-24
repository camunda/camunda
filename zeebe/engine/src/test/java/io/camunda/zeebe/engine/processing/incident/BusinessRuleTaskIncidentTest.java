/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;

/** Tests for incidents specific for business-rule-tasks. */
public class BusinessRuleTaskIncidentTest {

  private static final String PROCESS_ID = "process";
  private static final String TASK_ELEMENT_ID = "business-rule-task";

  private static final String DMN_RESOURCE = "/dmn/drg-force-user-with-assertions.dmn";
  private static final String DECISION_ID = "jedi_or_sith";
  private static final String DECISION_ID_VARIABLE = "decisionIdVariable";
  private static final String RESULT_VARIABLE = "result";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
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
    return assertIncidentCreated(
        processInstanceKey, elementInstanceKey, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  private IncidentRecordValueAssert assertIncidentCreated(
      final long processInstanceKey, final long elementInstanceKey, final String tenantId) {
    final var incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    return Assertions.assertThat(incidentRecord.getValue())
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(elementInstanceKey)
        .hasTenantId(tenantId)
        .hasJobKey(-1L)
        .hasVariableScopeKey(elementInstanceKey);
  }

  // --------------------------------------------------------------------------
  // ----- CalledDecision related tests

  @Test
  public void shouldCreateIncidentIfDecisionNotDeployed() {
    // given
    engine
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                b ->
                    b.zeebeCalledDecisionId("unknown_decision_id")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.CALLED_DECISION_ERROR)
        .hasErrorMessage(
            """
            Expected to evaluate decision 'unknown_decision_id', \
            but no decision found for id 'unknown_decision_id'\
            """);
  }

  @Test
  public void shouldCreateIncidentIfDecisionNotDeployedInSameDeploymentForBindingTypeDeployment() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();
    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                processWithBusinessRuleTask(
                    b ->
                        // an incident can only occur at run time if the target decision ID is an
                        // expression; static IDs are already checked at deploy time
                        b.zeebeCalledDecisionIdExpression(DECISION_ID_VARIABLE)
                            .zeebeBindingType(ZeebeBindingType.deployment)
                            .zeebeResultVariable(RESULT_VARIABLE)))
            .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(DECISION_ID_VARIABLE, DECISION_ID)
            .create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.CALLED_DECISION_ERROR)
        .hasErrorMessage(
            """
            Expected to evaluate decision '%s' with binding type 'deployment', \
            but no such decision found in the deployment with key %s which contained the current process. \
            To resolve this incident, migrate the process instance to a process definition \
            that is deployed together with the intended decision to evaluate.\
            """
                .formatted(DECISION_ID, deployment.getKey()));
  }

  @Test
  public void shouldCreateIncidentIfDecisionWithVersionTagNotDeployedForBindingTypeVersionTag() {
    // given
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();
    engine
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                b ->
                    b.zeebeCalledDecisionId(DECISION_ID)
                        .zeebeBindingType(ZeebeBindingType.versionTag)
                        .zeebeVersionTag("v1.0")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.CALLED_DECISION_ERROR)
        .hasErrorMessage(
            """
            Expected to evaluate decision with id 'jedi_or_sith' and version tag 'v1.0', but no such decision found. \
            To resolve this incident, deploy a decision with the given id and version tag.\
            """);
  }

  @Test
  public void shouldCreateIncidentIfDecisionEvaluationFailed() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.DECISION_EVALUATION_ERROR)
        .hasErrorMessage(
            """
            Expected to evaluate decision 'jedi_or_sith', but \
            Assertion failure on evaluate the expression \
            'assert(lightsaberColor, lightsaberColor != null)': The condition is not fulfilled""");
  }

  @Test
  public void shouldCreateIncidentIfDecisionIdExpressionEvaluationFailed() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b ->
                    b.zeebeCalledDecisionIdExpression(DECISION_ID_VARIABLE)
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // when
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'decisionIdVariable' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'decisionIdVariable'""");
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFails() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    // and an instance of that process is created without the required variables for the decision
    final var processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

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
  public void shouldResolveIncidentAfterDecisionNotDeployed() {
    // given
    engine
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Maps.of(entry("lightsaberColor", "blue")))
            .create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... deploy dmn to resolve issue
    engine.deployment().withXmlClasspathResource(DMN_RESOURCE).deploy();

    // ... resolve incident
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
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

  @Test
  public void shouldResolveIncidentAfterDecisionEvaluationFailed() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    engine
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("lightsaberColor", "blue")))
        .update();

    // ... resolve incident
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
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

  @Test
  public void shouldResolveIncidentAfterDecisionIdExpressionEvaluationFailed() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b ->
                    b.zeebeCalledDecisionIdExpression(DECISION_ID_VARIABLE)
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .deploy();

    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Maps.of(entry("lightsaberColor", "blue")))
            .create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    engine
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry(DECISION_ID_VARIABLE, DECISION_ID)))
        .update();

    // ... resolve incident
    engine.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.records()
                .betweenProcessInstance(processInstanceKey)
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

  @Test
  public void shouldCreateIncidentOnBusinessRuleTaskForCustomTenant() {
    // given
    final String tenantId = "acme";
    engine
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                b ->
                    b.zeebeCalledDecisionId("unknown_decision_id")
                        .zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .withTenantId(tenantId)
            .getFirst();

    // then
    assertIncidentCreated(processInstanceKey, taskActivating.getKey(), tenantId);
  }

  @Test
  public void shouldCreateIncidentOnBusinessRuleTaskForDifferentTenant() {
    // given
    final String tenantId = "acme";
    final String otherTenantId = "emca";
    engine
        .deployment()
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(tenantId)
        .deploy();
    engine
        .deployment()
        .withXmlClasspathResource(DMN_RESOURCE)
        .withXmlResource(
            processWithBusinessRuleTask(
                b -> b.zeebeCalledDecisionId(DECISION_ID).zeebeResultVariable(RESULT_VARIABLE)))
        .withTenantId(otherTenantId)
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    final var taskActivating =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(TASK_ELEMENT_ID)
            .withElementType(BpmnElementType.BUSINESS_RULE_TASK)
            .withTenantId(tenantId)
            .getFirst();

    // then
    assertThat(
            RecordingExporter.decisionRecords()
                .withIntent(DecisionIntent.CREATED)
                .withDecisionId(DECISION_ID)
                .withTenantId(otherTenantId)
                .exists())
        .isTrue();
    assertIncidentCreated(processInstanceKey, taskActivating.getKey(), tenantId);
  }
}
