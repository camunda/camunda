/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClusterVariableEvaluationContextTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String TENANT_A = "tenant_A";
  private static final String TENANT_B = "tenant_B";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void setUp() {
    ENGINE.tenant().newTenant().withTenantId(TENANT_A).create();
    ENGINE.tenant().newTenant().withTenantId(TENANT_B).create();
  }

  @Test
  public void checkGlobalClusterVariableIsResolved() {
    // Given
    ENGINE.clusterVariables().withName("KEY_1").withValue("\"_1_\"").setGlobalScope().create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_1")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_1",
                serviceTaskBuilder ->
                    serviceTaskBuilder.zeebeJobTypeExpression("camunda.vars.cluster.KEY_1"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_1").create();

    final var export =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(export).hasType("_1_");
  }

  @Test
  public void checkTenantClusterVariableIsResolved() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("KEY_2")
        .withValue("\"_2_\"")
        .withTenantId(TENANT_A)
        .setTenantScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_2")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_2",
                serviceTaskBuilder ->
                    serviceTaskBuilder.zeebeJobTypeExpression("camunda.vars.tenant.KEY_2"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();

    // When
    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_2").withTenantId(TENANT_A).create();

    assertThat(
            RecordingExporter.jobRecords()
                .withIntent(JobIntent.CREATED)
                .withProcessInstanceKey(processCreated)
                .withType("_2_")
                .withTenantId(TENANT_A)
                .exists())
        .isTrue();
  }

  @Test
  public void checkGlobalClusterNestedVariableIsResolved() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("JOB_CONFIG")
        .withValue(new JobConfiguration("DYNAMIC_TYPE", 10))
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_3")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_3",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobTypeExpression("camunda.vars.env.JOB_CONFIG.type")
                        .zeebeJobRetriesExpression("camunda.vars.env.JOB_CONFIG.retries"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_3").create();

    final var export =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(export).hasType("DYNAMIC_TYPE").hasRetries(10);
  }

  @Test
  public void checkGlobalClusterVariableIsNotResolvedWhenReferencedWithoutNamespace() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("MY_ASSIGNEE")
        .withValue("\"john_doe\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_4")
            .startEvent()
            .userTask(
                "MY_USER_TASK_1",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssigneeExpression(
                            "if MY_ASSIGNEE = null then \"default_assignee\" else MY_ASSIGNEE"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_4").create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(userTaskRecordValue).hasAssignee("default_assignee");
  }

  @Test
  public void checkGlobalClusterVariableTakesPriorityOverProcessVariable() {
    ENGINE
        .clusterVariables()
        .withName("KEY")
        .withValue("\"ClusterDefined\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_5")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_4",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobType("_1_")
                        .zeebeOutput("=camunda.vars.env.KEY", "resultJob"))
            .serviceTask(
                "MY_SERVICE_TASK_5", serviceTaskBuilder -> serviceTaskBuilder.zeebeJobType("_2_"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_5").create();

    ENGINE
        .job()
        .ofInstance(processCreated)
        .withType("_1_")
        .withVariables(
            Map.of("camunda", Map.of("vars", Map.of("env", Map.of("KEY", "ProcessDefined")))))
        .complete();

    final var job = ENGINE.jobs().withType("_2_").activate().getValue().getJobs().getFirst();

    assertThat(job.getVariables()).containsEntry("resultJob", "ProcessDefined");
  }

  @Test
  public void checkTenantClusterVariableOverridesGlobalClusterVariableInEnv() {
    // Given
    ENGINE.clusterVariables().withName("SVC").withValue("\"global\"").setGlobalScope().create();

    ENGINE
        .clusterVariables()
        .withName("SVC")
        .withValue("\"tenant\"")
        .withTenantId(TENANT_A)
        .setTenantScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_6")
            .startEvent()
            .serviceTask("MY_SERVICE_TASK_6", b -> b.zeebeJobTypeExpression("camunda.vars.env.SVC"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();

    // When
    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_6").withTenantId(TENANT_A).create();

    // Then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst();
    Assertions.assertThat(job.getValue()).hasType("tenant");
  }

  @Test
  public void checkEnvNamespaceContainsUnionOfGlobalAndTenantClusterVariable() {

    // Given
    ENGINE.clusterVariables().withName("A").withValue("\"globalA\"").setGlobalScope().create();

    ENGINE
        .clusterVariables()
        .withName("C")
        .withValue("\"tenantC\"")
        .withTenantId(TENANT_A)
        .setTenantScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_7")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_7",
                b -> b.zeebeJobTypeExpression("camunda.vars.env.A + \"-\" + camunda.vars.env.C"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();

    // When
    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_7").withTenantId(TENANT_A).create();

    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processCreated)
                .withElementId("MY_SERVICE_TASK_7")
                .withType("globalA-tenantC")
                .exists())
        .isTrue();
  }

  @Test
  public void checkEnvNamespaceFallsBackToGlobalWhenTenantMissingKey() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("ONLY_GLOBAL")
        .withValue("\"global\"")
        .setGlobalScope()
        .create();
    // no tenant value for ONLY_GLOBAL

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_8")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_8", b -> b.zeebeJobTypeExpression("camunda.vars.env.ONLY_GLOBAL"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();

    // When
    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_8").withTenantId(TENANT_A).create();

    // Then
    final var created =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst();
    Assertions.assertThat(created.getValue()).hasType("global");
  }

  @Test
  public void checkGlobalClusterVariableVisibleFromTenantProcess() {
    // Given
    ENGINE.clusterVariables().withName("GLOBAL").withValue("\"global\"").setGlobalScope().create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_9")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_9", b -> b.zeebeJobTypeExpression("camunda.vars.cluster.GLOBAL"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();
    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_9").withTenantId(TENANT_A).create();

    // Then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst();
    Assertions.assertThat(job.getValue()).hasType("global");
  }

  @Test
  public void checkTenantIsolationTenantVarNotVisibleToOtherTenant() {

    // Given
    ENGINE
        .clusterVariables()
        .withName("TENANT_SPECIFIC_KEY")
        .withValue("\"A_VAL\"")
        .withTenantId(TENANT_A)
        .setTenantScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_10")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_10",
                b ->
                    b.zeebeJobTypeExpression(
                        "if camunda.vars.cluster.TENANT_SPECIFIC_KEY = null then \"DEFAULT_JOB_TYPE\" else camunda.vars.cluster.TENANT_SPECIFIC_KEY"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_B).deploy();

    final var processCreated =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_10").withTenantId(TENANT_B).create();

    // Then
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst();
    Assertions.assertThat(job.getValue()).hasType("DEFAULT_JOB_TYPE");
  }

  @Test
  public void checkMultiTenantSameKeyResolvesPerTenant() {

    // Given
    ENGINE
        .clusterVariables()
        .withName("SVC_1")
        .withValue("\"svc-A\"")
        .withTenantId(TENANT_A)
        .setTenantScope()
        .create();

    ENGINE
        .clusterVariables()
        .withName("SVC_1")
        .withValue("\"svc-B\"")
        .withTenantId(TENANT_B)
        .setTenantScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_11")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_11", b -> b.zeebeJobTypeExpression("camunda.vars.tenant.SVC_1"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_A).deploy();
    ENGINE.deployment().withXmlResource(process).withTenantId(TENANT_B).deploy();

    // When
    final var processCreated1 =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_11").withTenantId(TENANT_A).create();
    final var processCreated2 =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_11").withTenantId(TENANT_B).create();

    final var j1 =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withTenantId(TENANT_A)
            .withProcessInstanceKey(processCreated1)
            .getFirst();
    final var j2 =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withTenantId(TENANT_B)
            .withProcessInstanceKey(processCreated2)
            .getFirst();

    // Then
    Assertions.assertThat(j1.getValue()).hasType("svc-A");
    Assertions.assertThat(j2.getValue()).hasType("svc-B");
  }

  @Test
  public void checkNotFoundClusterVariableReturnNullCleanly() {

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_12")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_12",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobType("_1_")
                        .zeebeOutput("=camunda.vars.env.KEY_3", "resultJob"))
            .serviceTask(
                "MY_SERVICE_TASK_13", serviceTaskBuilder -> serviceTaskBuilder.zeebeJobType("_2_"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_12").create();

    ENGINE.job().ofInstance(processInstanceKey).withType("_1_").complete().getValue();

    final var job = ENGINE.jobs().withType("_2_").activate().getValue().getJobs().getFirst();

    assertThat(job.getVariables()).containsEntry("resultJob", null);
  }

  @Test
  public void checkGlobalClusterVariableIsResolvedInRetries() {
    // Given
    ENGINE.clusterVariables().withName("JOB_RETRIES").withValue("1").setGlobalScope().create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_13")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_13",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobType("_1_")
                        .zeebeJobRetriesExpression("=camunda.vars.cluster.JOB_RETRIES"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_13").create();

    final var export =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(export).hasRetries(1);
  }

  @Test
  public void checkGlobalClusterVariableIsResolvedInsideUserTaskForAssignee() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("MY_ASSIGNEE_2")
        .withValue("\"john_doe\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_14")
            .startEvent()
            .userTask(
                "MY_USER_TASK_2",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssigneeExpression(
                            "if camunda.vars.cluster.MY_ASSIGNEE = null then \"default_assignee\" else camunda.vars.cluster.MY_ASSIGNEE_2"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_14").create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(userTaskRecordValue).hasAssignee("john_doe");
  }

  @Test
  public void checkGlobalClusterVariableIsResolvedInsideUserTaskForDueDate() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("DUE_DATE")
        .withValue("\"2040-01-01T00:00Z\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_15")
            .startEvent()
            .userTask(
                "MY_USER_TASK_3",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("CamundaUser")
                        .zeebeDueDateExpression("camunda.vars.cluster.DUE_DATE"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_15").create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(userTaskRecordValue).hasDueDate("2040-01-01T00:00Z");
  }

  @Test
  public void checkGlobalClusterVariableIsResolvedInsideUserTaskForFollowUpDate() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("FOLLOW_UP_DATE")
        .withValue("\"2040-01-01T00:00Z\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_16")
            .startEvent()
            .userTask(
                "MY_USER_TASK_4",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssignee("CamundaUser")
                        .zeebeFollowUpDateExpression("camunda.vars.cluster.FOLLOW_UP_DATE"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_16").create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    // Then
    Assertions.assertThat(userTaskRecordValue).hasFollowUpDate("2040-01-01T00:00Z");
  }

  @Test
  public void checkGlobalClusterVariableIsResolvedInsideCorrelationKey() {
    // Given
    ENGINE
        .clusterVariables()
        .withName("START_CORRELATION_KEY")
        .withValue("\"start\"")
        .setGlobalScope()
        .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID_17")
            .startEvent()
            .intermediateCatchEvent()
            .message(
                messageBuilder ->
                    messageBuilder
                        .name("message")
                        .zeebeCorrelationKeyExpression(
                            "camunda.vars.cluster.START_CORRELATION_KEY"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // When
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID_17").create();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processCreated)
                .onlyEvents()
                .limit(
                    r ->
                        r.getValue().getBpmnElementType()
                                == BpmnElementType.INTERMEDIATE_CATCH_EVENT
                            && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(record -> record.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .containsSubsequence(
            tuple(
                BpmnElementType.INTERMEDIATE_CATCH_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(
                BpmnElementType.INTERMEDIATE_CATCH_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  record JobConfiguration(String type, int retries) {}
}
