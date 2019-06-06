/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.broker.it.incident;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentCreated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentResolved;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCreated;
import static io.zeebe.protocol.intent.IncidentIntent.CREATED;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.ZeebeAssertHelper;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IncidentTest {
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeInput("foo", "foo"))
          .done();

  private static final String VARIABLES = "{\"foo\": \"bar\"}";

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Before
  public void setUp() {}

  @Test
  public void shouldRejectResolveOnNonExistingIncident() {
    // given

    final long nonExistingKey = Protocol.encodePartitionId(Protocol.DEPLOYMENT_PARTITION, 1);
    // when
    Assertions.assertThatThrownBy(
            () -> clientRule.getClient().newResolveIncidentCommand(nonExistingKey).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to resolve incident with key '"
                + nonExistingKey
                + "', but no such incident was found");
  }

  @Test
  public void shouldCreateAndResolveInputMappingIncident() {
    // given
    deploy();

    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    clientRule
        .getClient()
        .newSetVariablesCommand(workflowInstanceEvent.getWorkflowInstanceKey())
        .variables(VARIABLES)
        .send()
        .join();
    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send();

    // then
    assertJobCreated("test");
    assertIncidentResolved();
  }

  @Test
  public void shouldRejectDuplicateResolving() {
    // given
    deploy();

    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(CREATED).getFirst();

    // when
    clientRule
        .getClient()
        .newSetVariablesCommand(workflowInstanceEvent.getWorkflowInstanceKey())
        .variables(VARIABLES)
        .send()
        .join();
    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send();

    // then
    assertJobCreated("test");
    assertIncidentResolved();
    Assertions.assertThatThrownBy(
            () -> clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining(
            "Expected to resolve incident with key '"
                + incident.getKey()
                + "', but no such incident was found");
  }

  @Test
  public void shouldDeleteIncidentWhenWorkflowInstanceIsCanceled() {
    // given
    deploy();

    final WorkflowInstanceEvent workflowInstance =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    assertIncidentCreated();

    // when
    clientRule
        .getClient()
        .newCancelInstanceCommand(workflowInstance.getWorkflowInstanceKey())
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertIncidentResolved();
  }

  @Test
  public void shouldCreateAndResolveJobIncident() {
    // given a workflow instance with an open job
    deploy();

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .variables(VARIABLES)
        .send()
        .join();

    // when the job fails until it has no more retries left
    final ControllableJobHandler jobHandler = new ControllableJobHandler();
    jobHandler.failJob = true;

    clientRule
        .getClient()
        .newWorker()
        .jobType("test")
        .handler(jobHandler)
        .name("owner")
        .timeout(Duration.ofMinutes(5))
        .open();

    // then an incident is created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords().withIntent(CREATED).getFirst();

    // when the job retries are increased
    jobHandler.failJob = false;

    final ActivatedJob job = jobHandler.job;

    clientRule.getClient().newUpdateRetriesCommand(job.getKey()).retries(3).send().join();

    // and incident resolve command is send
    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send();

    // then the incident is resolved
    ZeebeAssertHelper.assertIncidentResolved();
    waitUntil(() -> jobHandler.jobCompleteCount.get() == 1);
  }

  @Test
  public void shouldCreateJobIncidentWithErrorMessage() {
    // given a workflow instance with an open job
    deploy();

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .variables(VARIABLES)
        .send()
        .join();

    // when the job fails until it has no more retries left
    final JobHandler jobHandler =
        (client, job) ->
            client.newFailCommand(job.getKey()).retries(0).errorMessage("failed message").send();

    clientRule
        .getClient()
        .newWorker()
        .jobType("test")
        .handler(jobHandler)
        .name("owner")
        .timeout(Duration.ofMinutes(5))
        .open();

    // then an incident is created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();
    assertThat(incident.getValue().getErrorMessage()).isEqualTo("failed message");
  }

  @Test
  public void shouldIncidentContainLastJobErrorMessage() {
    // given a workflow instance with an open job
    deploy();

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .variables(VARIABLES)
        .send()
        .join();

    // when the job fails until it has no more retries left
    final AtomicInteger retries = new AtomicInteger(1);
    final JobHandler jobHandler =
        (client, job) -> {
          final int retryCount = retries.getAndDecrement();
          client
              .newFailCommand(job.getKey())
              .retries(retryCount)
              .errorMessage(retryCount + " message")
              .send();
        };

    clientRule
        .getClient()
        .newWorker()
        .jobType("test")
        .handler(jobHandler)
        .name("owner")
        .timeout(Duration.ofMinutes(5))
        .open();

    // then an incident is created
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();
    assertThat(incident.getValue().getErrorMessage()).isEqualTo("0 message");
  }

  private void deploy() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  private static final class ControllableJobHandler implements JobHandler {
    AtomicInteger jobCompleteCount = new AtomicInteger(0);
    boolean failJob = false;
    ActivatedJob job;

    @Override
    public void handle(final JobClient client, final ActivatedJob job) {
      this.job = job;

      if (failJob) {
        throw new RuntimeException("expected failure");
      } else {
        client.newCompleteCommand(job.getKey()).variables("{}").send().join();
        jobCompleteCount.incrementAndGet();
      }
    }
  }
}
