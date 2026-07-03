/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.engine.processing.incident.IncidentHelper.assertIncidentCreated;
import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.processinstance.BusinessIdValidator;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CallActivityIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID_VARIABLE = "wfChild";

  private static final Function<String, BpmnModelInstance>
      PROCESS_PARENT_PROCESS_ID_EXPRESSION_SUPPLIER =
          (parentProcessId) ->
              Bpmn.createExecutableProcess(parentProcessId)
                  .startEvent()
                  .callActivity("call", c -> c.zeebeProcessIdExpression(PROCESS_ID_VARIABLE))
                  .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String parentProcessId;
  private String childProcessId;

  @Before
  public void init() {
    parentProcessId = Strings.newRandomValidBpmnId();
    childProcessId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .done())
        .deploy();
  }

  @Test
  public void shouldCreateIncidentIfProcessIsNotDeployed() {
    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.CALLED_ELEMENT_ERROR)
        .hasErrorMessage(
            "Expected process with BPMN process id '"
                + childProcessId
                + "' to be deployed, but not found.");
  }

  @Test
  public void shouldCreateIncidentIfProcessIsNotDeployedInSameDeploymentForBindingTypeDeployment() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var childProcess = Bpmn.createExecutableProcess(childProcessId).startEvent().done();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcess).deploy();
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    // an incident can only occur at run time if the target process ID is an
                    // expression; static IDs are already checked at deploy time
                    c.zeebeProcessIdExpression(PROCESS_ID_VARIABLE)
                        .zeebeBindingType(ZeebeBindingType.deployment))
            .done();
    final var deployment =
        ENGINE.deployment().withXmlResource("wf-parent.bpmn", parentProcess).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withVariable(PROCESS_ID_VARIABLE, childProcessId)
            .create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.CALLED_ELEMENT_ERROR)
        .hasErrorMessage(
            """
            Expected to call process with BPMN process id '%s' with binding type 'deployment', \
            but no such process found in the deployment with key %s which contained the current process. \
            To resolve this incident, migrate the process instance to a process definition \
            that is deployed together with the intended process definition to call.\
            """
                .formatted(childProcessId, deployment.getKey()));
  }

  @Test
  public void shouldCreateIncidentIfProcessWithVersionTagIsNotDeployedForBindingTypeVersionTag() {
    // given
    final var childProcessId = Strings.newRandomValidBpmnId();
    final var childProcess = Bpmn.createExecutableProcess(childProcessId).startEvent().done();
    ENGINE.deployment().withXmlResource("wf-child.bpmn", childProcess).deploy();
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(childProcessId)
                        .zeebeBindingType(ZeebeBindingType.versionTag)
                        .zeebeVersionTag("v1.0"))
            .done();
    ENGINE.deployment().withXmlResource("wf-parent.bpmn", parentProcess).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.CALLED_ELEMENT_ERROR)
        .hasErrorMessage(
            """
            Expected to call process with BPMN process id '%s' and version tag '%s', but no such process found. \
            To resolve this incident, deploy a process with the given process id and version tag.\
            """
                .formatted(childProcessId, "v1.0"));
  }

  @Test
  public void shouldCreateIncidentIfProcessHasNoNoneStartEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .message("start")
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasElementInstancePath(List.of(List.of(processInstanceKey, elementInstance.getKey())))
        .hasRootProcessInstanceKey(processInstanceKey)
        .hasProcessDefinitionKey(incident.getValue().getProcessDefinitionKey())
        .hasErrorType(ErrorType.CALLED_ELEMENT_ERROR)
        .hasErrorMessage(
            "Expected process with BPMN process id '"
                + childProcessId
                + "' to have a none start event, but not found.");
  }

  @Test
  public void shouldCreateIncidentIfProcessIdVariableNotExists() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(PROCESS_PARENT_PROCESS_ID_EXPRESSION_SUPPLIER.apply(parentProcessId))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'wfChild' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'wfChild'""");
  }

  @Test
  public void shouldCreateIncidentIfProcessIdVariableIsNaString() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(PROCESS_PARENT_PROCESS_ID_EXPRESSION_SUPPLIER.apply(parentProcessId))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withVariable(PROCESS_ID_VARIABLE, 123)
            .create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression '"
                + PROCESS_ID_VARIABLE
                + "' to be 'STRING', but was 'NUMBER'.");
  }

  @Test
  public void shouldCreateIncidentOnCallActivityForCustomTenant() {
    // given
    final String tenantId = "acme";
    ENGINE
        .deployment()
        .withXmlResource(PROCESS_PARENT_PROCESS_ID_EXPRESSION_SUPPLIER.apply(parentProcessId))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).withTenantId(tenantId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance, tenantId)
        .hasElementInstancePath(List.of(List.of(processInstanceKey, elementInstance.getKey())))
        .hasRootProcessInstanceKey(processInstanceKey)
        .hasProcessDefinitionKey(incident.getValue().getProcessDefinitionKey())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected result of the expression 'wfChild' to be 'STRING', but was 'NULL'. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'wfChild'""");
  }

  @Test
  public void shouldResolveIncident() {
    // given
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);

    // when
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done())
        .deploy();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withRecordKey(incident.getValue().getElementInstanceKey())
                .limit(2))
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldResolveIncidentWithMessageBoundaryEvent() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity("call", c -> c.zeebeProcessId(childProcessId))
                .boundaryEvent("boundary")
                .message(m -> m.name("message").zeebeCorrelationKeyExpression("123"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);

    // when
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done())
        .deploy();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withRecordKey(incident.getValue().getElementInstanceKey())
                .limit(2))
        .extracting(Record::getIntent)
        .contains(ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  // Regression test for https://github.com/camunda/camunda/issues/50014.
  @Test
  public void shouldExposeWrappingSubprocessInElementInstancePathWhenCallActivityIsNested() {
    // given
    final var wrappedParentProcessId = Strings.newRandomValidBpmnId();
    final var wrapperSubprocessId = "wrapper-subprocess";
    final var wrappedCallActivityId = "call";
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent-wrapped.bpmn",
            Bpmn.createExecutableProcess(wrappedParentProcessId)
                .startEvent()
                .subProcess(
                    wrapperSubprocessId,
                    sp ->
                        sp.embeddedSubProcess()
                            .startEvent()
                            .callActivity(
                                wrappedCallActivityId, c -> c.zeebeProcessId(childProcessId))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final var failingJobType = "failing-" + Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("failing", t -> t.zeebeJobType(failingJobType))
                .endEvent()
                .done())
        .deploy();

    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(wrappedParentProcessId).create();

    final long wrapperSubprocessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.SUB_PROCESS)
            .getFirst()
            .getKey();

    final long callActivityInstanceKey = getCallActivityInstance(parentProcessInstanceKey).getKey();

    final long childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    final Record<JobRecordValue> failingJob =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .withType(failingJobType)
            .getFirst();
    final long failingElementInstanceKey = failingJob.getValue().getElementInstanceKey();

    // when
    ENGINE.job().ofInstance(childProcessInstanceKey).withType(failingJobType).withRetries(0).fail();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst();

    assertThat(incident.getValue().getElementInstancePath())
        .containsExactly(
            List.of(
                parentProcessInstanceKey, wrapperSubprocessInstanceKey, callActivityInstanceKey),
            List.of(childProcessInstanceKey, failingElementInstanceKey));
  }

  // Regression test for https://github.com/camunda/camunda/issues/50014.
  @Test
  public void shouldExposeFullCallHierarchyWhenIncidentOccursAfterChildHasProgressed() {
    // given
    final var firstJobType = "first-" + Strings.newRandomValidBpmnId();
    final var failingJobType = "failing-" + Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-child.bpmn",
            Bpmn.createExecutableProcess(childProcessId)
                .startEvent()
                .serviceTask("first", t -> t.zeebeJobType(firstJobType))
                .serviceTask("failing", t -> t.zeebeJobType(failingJobType))
                .endEvent()
                .done())
        .deploy();

    final long parentProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final long callActivityInstanceKey = getCallActivityInstance(parentProcessInstanceKey).getKey();
    final long childProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withParentProcessInstanceKey(parentProcessInstanceKey)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();

    ENGINE.job().ofInstance(childProcessInstanceKey).withType(firstJobType).complete();

    final long failingElementInstanceKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .withType(failingJobType)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    ENGINE.job().ofInstance(childProcessInstanceKey).withType(failingJobType).withRetries(0).fail();

    // then
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(childProcessInstanceKey)
            .getFirst();

    assertThat(incident.getValue().getElementInstancePath())
        .containsExactly(
            List.of(parentProcessInstanceKey, callActivityInstanceKey),
            List.of(childProcessInstanceKey, failingElementInstanceKey));
  }

  @Test
  public void shouldNotCreateIncidentWhenBusinessIdIsExplicitNull() {
    // given - an explicit null discards the business id
    deployParentWithChildBusinessId("=null");

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withBusinessId("parent-business-id")
            .create();

    // then - no incident; the child is created with no business id
    Assertions.assertThat(getChildProcessInstance(processInstanceKey).getValue()).hasBusinessId("");
  }

  @Test
  public void shouldNotCreateIncidentWhenBusinessIdFeelEvaluatesToEmptyString() {
    // given - a FEEL expression evaluating to an empty string is treated like '=null'
    deployParentWithChildBusinessId("=\"\"");

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withBusinessId("parent-business-id")
            .create();

    // then - no incident; the child is created with no business id
    Assertions.assertThat(getChildProcessInstance(processInstanceKey).getValue()).hasBusinessId("");
  }

  @Test
  public void shouldCreateIncidentWhenBusinessIdVariableNotExists() {
    // given - a FEEL expression referencing a variable that is never provided
    deployParentWithChildBusinessId("=businessIdVar");

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Expected to resolve the business id for the call activity from expression 'businessIdVar', but it evaluated to null. \
            The evaluation reported the following warnings:
            [NO_VARIABLE_FOUND] No variable found with name 'businessIdVar'""");
  }

  @Test
  public void shouldCreateIncidentWhenBusinessIdFeelResolvesToTooLongValue() {
    // given - a FEEL expression whose variable resolves to a value longer than the maximum
    deployParentWithChildBusinessId("=businessIdVar");

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(parentProcessId)
            .withVariable(
                "businessIdVar", "b".repeat(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH + 1))
            .create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected to resolve a valid business id for the call activity, but it exceeds the max length of %d."
                .formatted(BusinessIdValidator.MAX_BUSINESS_ID_LENGTH));
  }

  @Test
  public void shouldCreateIncidentWhenBusinessIdResolvesToNonString() {
    // given - a FEEL expression that resolves to a number
    deployParentWithChildBusinessId("=42");

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final Record<ProcessInstanceRecordValue> elementInstance =
        getCallActivityInstance(processInstanceKey);

    assertIncidentCreated(incident, elementInstance)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected the business id for the call activity to resolve to a string, but expression '42' evaluated to 'NUMBER'.");
  }

  @Test
  public void shouldResolveIncidentAfterProvidingBusinessIdVariable() {
    // given - a missing-variable incident at the call activity
    deployParentWithChildBusinessId("=businessIdVar");
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);

    // when - the variable is provided and the incident is resolved
    ENGINE
        .variables()
        .ofScope(processInstanceKey)
        .withDocument(Map.of("businessIdVar", "resolved-business-id"))
        .update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then - the child is created with the now-resolvable business id
    Assertions.assertThat(getChildProcessInstance(processInstanceKey).getValue())
        .hasBusinessId("resolved-business-id");
  }

  @Test
  public void shouldResolveBusinessIdIncidentAfterMigratingToFixedVersion() {
    // given - a source version whose call activity business id references a variable that is
    // never provided, and a target version whose call activity resolves it from an available one
    final String sourceProcessId = Strings.newRandomValidBpmnId();
    final String targetProcessId = Strings.newRandomValidBpmnId();
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                "wf-child.bpmn",
                Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done())
            .withXmlResource(
                "wf-source.bpmn",
                Bpmn.createExecutableProcess(sourceProcessId)
                    .startEvent()
                    .callActivity(
                        "call",
                        c -> c.zeebeProcessId(childProcessId).zeebeBusinessId("=businessIdVar"))
                    .done())
            .withXmlResource(
                "wf-target.bpmn",
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .callActivity(
                        "call", c -> c.zeebeProcessId(childProcessId).zeebeBusinessId("=orderId"))
                    .done())
            .deploy();
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(sourceProcessId)
            .withVariable("orderId", "migrated-business-id")
            .create();
    final Record<IncidentRecordValue> incident = getIncident(processInstanceKey);
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    // when - the instance is migrated to the fixed version and the incident is resolved
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("call", "call")
        .migrate();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then - the child is created with the business id re-evaluated from the migrated definition
    Assertions.assertThat(getChildProcessInstance(processInstanceKey).getValue())
        .hasBusinessId("migrated-business-id");
  }

  private void deployParentWithChildBusinessId(final String businessId) {
    ENGINE
        .deployment()
        .withXmlResource(
            "wf-parent.bpmn",
            Bpmn.createExecutableProcess(parentProcessId)
                .startEvent()
                .callActivity(
                    "call", c -> c.zeebeProcessId(childProcessId).zeebeBusinessId(businessId))
                .done())
        .withXmlResource(
            "wf-child.bpmn",
            Bpmn.createExecutableProcess(childProcessId).startEvent().endEvent().done())
        .deploy();
  }

  private Record<ProcessInstanceRecordValue> getChildProcessInstance(
      final long parentProcessInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withParentProcessInstanceKey(parentProcessInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .getFirst();
  }

  private Record<ProcessInstanceRecordValue> getCallActivityInstance(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.CALL_ACTIVITY)
        .getFirst();
  }

  private Record<IncidentRecordValue> getIncident(final long processInstanceKey) {
    return RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }
}
