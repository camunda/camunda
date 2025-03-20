/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilder;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Verifies the behavior of elements that are based on jobs and should be processed by job workers.
 * For example, service tasks.
 */
@RunWith(Parameterized.class)
public final class JobWorkerElementTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public JobWorkerElementBuilder elementBuilder;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return JobWorkerElementBuilderProvider.buildersAsParameters();
  }

  private BpmnModelInstance process(
      final Consumer<ZeebeJobWorkerElementBuilder<?>> elementModifier) {
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();

    final var jobWorkerElementBuilder = elementBuilder.build(processBuilder, elementModifier);
    return jobWorkerElementBuilder.id("task").done();
  }

  @Test
  public void shouldActivateTask() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(elementBuilder.getElementType())
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId("task")
        .hasBpmnElementType(elementBuilder.getElementType())
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateTaskWithCustomTenant() {
    // given
    final String tenantId = "foo";
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test")))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivating =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withTenantId(tenantId)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    Assertions.assertThat(taskActivating.getValue())
        .hasElementId("task")
        .hasBpmnElementType(elementBuilder.getElementType())
        .hasFlowScopeKey(processInstanceKey)
        .hasBpmnProcessId("process")
        .hasProcessInstanceKey(processInstanceKey)
        .hasTenantId(tenantId);
  }

  @Test
  public void shouldCreateJob() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test").zeebeJobRetries("5")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue())
        .hasType("test")
        .hasJobKind(JobKind.BPMN_ELEMENT)
        .hasJobListenerEventType(JobListenerEventType.UNSPECIFIED)
        .hasRetries(5)
        .hasElementInstanceKey(taskActivated.getKey())
        .hasElementId(taskActivated.getValue().getElementId())
        .hasProcessDefinitionKey(taskActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(taskActivated.getValue().getBpmnProcessId())
        .hasProcessDefinitionVersion(taskActivated.getValue().getVersion());
  }

  @Test
  public void shouldCreateJobWithCustomHeaders() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(
                t -> t.zeebeJobType("test").zeebeTaskHeader("a", "b").zeebeTaskHeader("c", "d")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final Map<String, String> customHeaders = jobCreated.getValue().getCustomHeaders();
    assertThat(customHeaders).hasSize(2).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCreateJobWithVariables() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            process(t -> t.zeebeInputExpression("x", "y").zeebeJobType("taskWithVariables")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("x", 1).create();

    // then
    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("y")
            .getFirst();

    Assertions.assertThat(variableCreated.getValue()).hasValue("1");

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    final List<JobRecordValue> activatedJobs =
        ENGINE.jobs().withType("taskWithVariables").activate().getValue().getJobs();

    assertThat(activatedJobs)
        .hasSize(1)
        .allSatisfy(
            job -> assertThat(job.getVariables()).containsEntry("x", 1).containsEntry("y", 1));
  }

  @Test
  public void shouldCompleteTask() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobType("test"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType("test").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteTaskWithCustomTenant() {
    // given
    final String tenantId = Strings.newRandomValidIdentityId();
    final String username = Strings.newRandomValidIdentityId();
    ENGINE.tenant().newTenant().withTenantId(tenantId).create();
    ENGINE.user().newUser(username).create();
    ENGINE
        .tenant()
        .addEntity(tenantId)
        .withEntityId(username)
        .withEntityType(EntityType.USER)
        .add();
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test")))
        .withTenantId(tenantId)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(tenantId).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("test")
        .withAuthorizedTenantIds(tenantId)
        .complete(username);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withTenantId(tenantId)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(elementBuilder.getElementType(), ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateJobWithJobTypeExpression() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("type"))).deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("type", "test").create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasType("test");
  }

  @Test
  public void shouldCreateJobWithJobRetriesExpression() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobType("test").zeebeJobRetriesExpression("retries")))
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).withVariable("retries", 8).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasRetries(8);
  }
}
