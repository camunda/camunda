/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class LongPollingActivationTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public ExpectedException exception = ExpectedException.none();

  private String processId;

  @Before
  public void deployProcess() {
    processId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask("task", t -> t.zeebeTaskType("foo"))
            .endEvent("end")
            .done();

    final DeploymentEvent deploymentEvent =
        CLIENT_RULE
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(modelInstance, "workflow.bpmn")
            .send()
            .join();
    CLIENT_RULE.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  @Test
  public void shouldSendActivatedJobToOpenWorker() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    sendActivateRequests(3);
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType("foo")
        .handler((c, j) -> latch.countDown())
        .requestTimeout(Duration.ofMinutes(11))
        .name("open")
        .open();
    sendActivateRequests(3);

    // when
    final WorkflowInstanceEvent workflowInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(processId)
            .latestVersion()
            .send()
            .join();

    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();

    final Record<JobRecordValue> jobRecord =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED)
            .withType("foo")
            .withWorkflowInstanceKey(workflowInstanceEvent.getWorkflowInstanceKey())
            .getFirst();

    assertThat(jobRecord.getValue().getWorker()).isEqualTo("open");
  }

  private void sendActivateRequests(int count) throws InterruptedException {
    for (int i = 0; i < count; i++) {
      final ZeebeClient client =
          ZeebeClient.newClientBuilder()
              .brokerContactPoint(BROKER_RULE.getGatewayAddress().toString())
              .usePlaintext()
              .defaultJobTimeout(Duration.ofMinutes(10))
              .defaultRequestTimeout(Duration.ofMinutes(10))
              .build();

      client
          .newActivateJobsCommand()
          .jobType("foo")
          .maxJobsToActivate(10)
          .workerName("closed-" + i)
          .requestTimeout(Duration.ofMinutes(10))
          .send();

      Thread.sleep(100);
      client.close();
    }
  }
}
