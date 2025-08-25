/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ClusterVariableEvaluationContextTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void checkClusterVariableIsResolved() {
    ENGINE.variable().withClusterVariable("KEY_1", "_1_").create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_1",
                serviceTaskBuilder ->
                    serviceTaskBuilder.zeebeJobTypeExpression("camunda.vars.env.KEY_1"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID").create();

    final var export =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    Assertions.assertThat(export).hasType("_1_");
  }

  @Test
  public void checkClusterNestedVariableIsResolved() {
    final Record<VariableRecordValue> result =
        ENGINE
            .variable()
            .withClusterVariable("JOB_CONFIG", new JobConfiguration("DYNAMIC_TYPE", 10))
            .create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_2",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobTypeExpression("camunda.vars.env.JOB_CONFIG.type")
                        .zeebeJobRetriesExpression("camunda.vars.env.JOB_CONFIG.retries"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processCreated = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID").create();

    final var export =
        RecordingExporter.jobRecords()
            .withIntent(JobIntent.CREATED)
            .withProcessInstanceKey(processCreated)
            .getFirst()
            .getValue();

    Assertions.assertThat(export).hasType("DYNAMIC_TYPE").hasRetries(10);
  }

  @Test
  public void checkClusterVariableIsNotResolvedWhenReferencedWithoutNamespace() {
    ENGINE.variable().withClusterVariable("MY_ASSIGNEE", "john_doe").create();
    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .userTask(
                "MY_USER_TASK",
                t ->
                    t.zeebeUserTask()
                        .zeebeAssigneeExpression(
                            "if MY_ASSIGNEE = null then \"default_assignee\" else MY_ASSIGNEE"))
            .endEvent()
            .done();

    // when
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID").create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(userTaskRecordValue).hasAssignee("default_assignee");
  }

  @Test
  public void checkClusterVariableNotCoverProcessInstanceVariable() {
    // given
    ENGINE.variable().withClusterVariable("ASSIGNEE_VAR", "john_doe").create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .userTask(
                "MY_USER_TASK", t -> t.zeebeUserTask().zeebeAssigneeExpression("ASSIGNEE_VAR"))
            .endEvent()
            .done();

    // when
    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS_ID")
            .withVariable("ASSIGNEE_VAR", "alex_doe")
            .create();

    final var userTaskRecordValue =
        RecordingExporter.userTaskRecords(UserTaskIntent.ASSIGNED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue();

    Assertions.assertThat(userTaskRecordValue).hasAssignee("alex_doe");
  }

  @Test
  public void checkProcessVariableTakesPriorityOverClusterVariable() {
    ENGINE.variable().withClusterVariable("KEY", "ClusterDefined").create();

    final var process =
        Bpmn.createExecutableProcess("PROCESS_ID")
            .startEvent()
            .serviceTask(
                "MY_SERVICE_TASK_1",
                serviceTaskBuilder ->
                    serviceTaskBuilder
                        .zeebeJobType("_1_")
                        .zeebeOutput("=camunda.vars.env.KEY", "resultJob"))
            .serviceTask(
                "MY_SERVICE_TASK_2", serviceTaskBuilder -> serviceTaskBuilder.zeebeJobType("_2_"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("PROCESS_ID").create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("_1_")
        .withVariables(
            Map.of("camunda", Map.of("vars", Map.of("env", Map.of("KEY", "ProcessDefined")))))
        .complete();

    final var job = ENGINE.jobs().withType("_2_").activate().getValue().getJobs().getFirst();

    assertThat(job.getVariables()).containsEntry("resultJob", "ProcessDefined");
  }

  record JobConfiguration(String type, int retries) {}
}
