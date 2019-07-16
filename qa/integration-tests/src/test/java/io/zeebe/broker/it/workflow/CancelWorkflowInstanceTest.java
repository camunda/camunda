/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCanceled;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCanceled;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class CancelWorkflowInstanceTest {
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeTaskType("test"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // when
    clientRule
        .getClient()
        .newCancelInstanceCommand(workflowInstance.getWorkflowInstanceKey())
        .send()
        .join();

    // then
    assertWorkflowInstanceCanceled("process");
  }

  @Test
  public void shouldFailToCompleteJobAfterCancel() {
    // given
    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    final ActivatedJob job =
        TestUtil.doRepeatedly(
                () ->
                    clientRule
                        .getClient()
                        .newActivateJobsCommand()
                        .jobType("test")
                        .maxJobsToActivate(1)
                        .send()
                        .join())
            .until(response -> !response.getJobs().isEmpty())
            .getJobs()
            .get(0);

    clientRule
        .getClient()
        .newCancelInstanceCommand(workflowInstance.getWorkflowInstanceKey())
        .send()
        .join();

    waitUntil(() -> RecordingExporter.jobRecords(JobIntent.CANCEL).exists());

    // when
    assertThatThrownBy(
            () -> {
              clientRule.getClient().newCompleteCommand(job.getKey()).send().join();
            })
        .isInstanceOf(ClientException.class);

    // then
    assertJobCanceled();
    assertWorkflowInstanceCanceled("process");
  }

  @Test
  public void shouldNotCancelElementInstance() {
    // given
    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    final Record<JobRecordValue> record =
        RecordingExporter.jobRecords().withType("test").getFirst();

    // when - then
    final long elementInstanceKey = record.getValue().getElementInstanceKey();
    assertThatThrownBy(
            () -> {
              clientRule.getClient().newCancelInstanceCommand(elementInstanceKey).send().join();
            })
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to cancel a workflow instance with key '"
                + elementInstanceKey
                + "', but no such workflow was found");
  }
}
