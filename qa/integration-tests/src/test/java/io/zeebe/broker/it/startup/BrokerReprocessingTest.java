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
package io.zeebe.broker.it.startup;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementActivated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertElementReady;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentCreated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentResolveFailed;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertIncidentResolved;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertJobCreated;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCreated;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;

import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftServiceNames;
import io.zeebe.raft.state.RaftState;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BrokerReprocessingTest {
  private static final String NULL_PAYLOAD = "{}";

  @Parameters(name = "{index}: {1}")
  public static Object[][] reprocessingTriggers() {
    return new Object[][] {
      new Object[] {
        new Consumer<BrokerReprocessingTest>() {
          @Override
          public void accept(final BrokerReprocessingTest t) {
            t.restartBroker();
          }
        },
        "restart"
      },
      new Object[] {
        new Consumer<BrokerReprocessingTest>() {
          @Override
          public void accept(final BrokerReprocessingTest t) {
            t.deleteSnapshotsAndRestart();
          }
        },
        "restart-without-snapshot"
      }
    };
  }

  @Parameter(0)
  public Consumer<BrokerReprocessingTest> reprocessingTrigger;

  @Parameter(1)
  public String name;

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeTaskType("foo"))
          .endEvent("end")
          .done();

  private static final BpmnModelInstance WORKFLOW_TWO_TASKS =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task1", t -> t.zeebeTaskType("foo"))
          .serviceTask("task2", t -> t.zeebeTaskType("bar"))
          .endEvent("end")
          .done();

  private static final BpmnModelInstance WORKFLOW_INCIDENT =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeTaskType("test").zeebeInput("$.foo", "$.foo"))
          .endEvent("end")
          .done();

  private static final BpmnModelInstance WORKFLOW_MESSAGE =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldCreateWorkflowInstanceAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    // then
    assertWorkflowInstanceCreated();
  }

  @Test
  public void shouldContinueWorkflowInstanceAtTaskAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    assertJobCreated("foo");

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload(NULL_PAYLOAD).send())
        .open();

    // then
    assertJobCompleted();
    assertWorkflowInstanceCompleted("process");
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1518")
  public void shouldContinueWorkflowInstanceWithLockedTaskAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();
    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    waitUntil(() -> !recordingJobHandler.getHandledJobs().isEmpty());

    // when
    reprocessingTrigger.accept(this);

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);

    clientRule.getJobClient().newCompleteCommand(jobEvent.getKey()).send().join();

    // then
    assertJobCompleted();
    assertWorkflowInstanceCompleted("process");
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1518")
  public void shouldContinueWorkflowInstanceAtSecondTaskAfterRestart() throws Exception {
    // given
    deploy(WORKFLOW_TWO_TASKS, "two-tasks.bpmn");

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload(NULL_PAYLOAD).send())
        .open();

    final CountDownLatch latch = new CountDownLatch(1);
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("bar")
        .handler((client, job) -> latch.countDown())
        .open();
    latch.await();

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("bar")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload(NULL_PAYLOAD).send())
        .open();

    // then
    assertJobCompleted("foo");
    assertJobCreated("bar");
    assertWorkflowInstanceCompleted("process");
  }

  @Test
  public void shouldDeployNewWorkflowVersionAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    // when
    reprocessingTrigger.accept(this);

    final DeploymentEvent deploymentResult =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentResult.getWorkflows().get(0).getVersion()).isEqualTo(2);

    final WorkflowInstanceEvent workflowInstanceV1 =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .version(1)
            .send()
            .join();

    final WorkflowInstanceEvent workflowInstanceV2 =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(workflowInstanceV1.getVersion()).isEqualTo(1);
    assertThat(workflowInstanceV2.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldLockAndCompleteStandaloneJobAfterRestart() {
    // given
    clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();
    assertJobCreated("foo");

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("foo")
        .handler(
            (client, job) -> client.newCompleteCommand(job.getKey()).payload(NULL_PAYLOAD).send())
        .open();

    // then
    assertJobCompleted("foo");
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1518")
  public void shouldCompleteStandaloneJobAfterRestart() {
    // given
    clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();

    final RecordingJobHandler recordingJobHandler = new RecordingJobHandler();
    clientRule.getJobClient().newWorker().jobType("foo").handler(recordingJobHandler).open();

    waitUntil(() -> !recordingJobHandler.getHandledJobs().isEmpty());

    // when
    reprocessingTrigger.accept(this);

    final ActivatedJob jobEvent = recordingJobHandler.getHandledJobs().get(0);
    clientRule.getJobClient().newCompleteCommand(jobEvent.getKey()).send().join();

    // then
    assertJobCompleted("foo");
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1518")
  public void shouldNotReceiveLockedJobAfterRestart() {
    // given
    clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    clientRule.getJobClient().newWorker().jobType("foo").handler(jobHandler).open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    // when
    reprocessingTrigger.accept(this);

    jobHandler.clear();

    clientRule.getJobClient().newWorker().jobType("foo").handler(jobHandler).open();

    // then
    TestUtil.doRepeatedly(() -> null)
        .whileConditionHolds((o) -> jobHandler.getHandledJobs().isEmpty());

    assertThat(jobHandler.getHandledJobs()).isEmpty();
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1518")
  public void shouldReceiveLockExpiredJobAfterRestart() {
    // given
    clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    final JobWorker subscription =
        clientRule.getJobClient().newWorker().jobType("foo").handler(jobHandler).open();

    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());
    subscription.close();

    // when
    reprocessingTrigger.accept(this);

    doRepeatedly(
            () -> {
              brokerRule
                  .getClock()
                  .addTime(Duration.ofSeconds(60)); // retriggers lock expiration check in broker
              return null;
            })
        .until(t -> RecordingExporter.jobRecords(JobIntent.TIMED_OUT).exists());
    jobHandler.clear();

    clientRule.getJobClient().newWorker().jobType("foo").handler(jobHandler).open();

    // then
    waitUntil(() -> !jobHandler.getHandledJobs().isEmpty());

    final ActivatedJob jobEvent = jobHandler.getHandledJobs().get(0);
    clientRule.getJobClient().newCompleteCommand(jobEvent.getKey()).send().join();

    assertJobCompleted();
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1033")
  public void shouldResolveIncidentAfterRestart() {
    // given
    deploy(WORKFLOW_INCIDENT, "incident.bpmn");

    final WorkflowInstanceEvent instance =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    assertIncidentCreated();
    assertElementReady("task");

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(instance.getWorkflowInstanceKey())
        .payload("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    assertIncidentResolved();
    assertJobCreated("test");
  }

  @Test
  @Ignore("https://github.com/zeebe-io/zeebe/issues/1033")
  public void shouldResolveFailedIncidentAfterRestart() {
    // given
    deploy(WORKFLOW_INCIDENT, "incident.bpmn");

    final WorkflowInstanceEvent instanceEvent =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .send()
            .join();

    assertIncidentCreated();
    assertElementReady("task");

    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(instanceEvent.getWorkflowInstanceKey())
        .payload("{\"x\":\"y\"}")
        .send()
        .join();

    assertIncidentResolveFailed();

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(instanceEvent.getWorkflowInstanceKey())
        .payload("{\"foo\":\"bar\"}")
        .send()
        .join();

    // then
    assertIncidentResolved();
    assertJobCreated("test");
  }

  @Test
  public void shouldLoadRaftConfiguration() {
    // given
    final int testTerm = 8;

    final ServiceName<Raft> serviceName =
        RaftServiceNames.raftServiceName(Partition.getPartitionName(0));

    final Raft raft = brokerRule.getService(serviceName);
    waitUntil(() -> raft.getState() == RaftState.LEADER);

    raft.setTerm(testTerm);

    // when
    reprocessingTrigger.accept(this);

    final Raft raftAfterRestart = brokerRule.getService(serviceName);
    waitUntil(() -> raftAfterRestart.getState() == RaftState.LEADER);

    // then
    assertThat(raftAfterRestart.getState()).isEqualTo(RaftState.LEADER);
    assertThat(raftAfterRestart.getTerm()).isGreaterThanOrEqualTo(9);
    assertThat(raftAfterRestart.getMemberSize()).isEqualTo(0);
    assertThat(raftAfterRestart.getVotedFor()).isEqualTo(0);
  }

  @Test
  public void shouldAssignUniqueWorkflowInstanceKeyAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    final long workflowInstance1Key = startWorkflowInstance("process").getWorkflowInstanceKey();

    // when
    reprocessingTrigger.accept(this);

    final long workflowInstance2Key = startWorkflowInstance("process").getWorkflowInstanceKey();

    // then
    assertThat(workflowInstance2Key).isGreaterThan(workflowInstance1Key);
  }

  @Test
  public void shouldAssignUniqueJobKeyAfterRestart() {
    // given
    deploy(WORKFLOW, "workflow.bpmn");

    final Supplier<JobEvent> jobCreator =
        () -> clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();

    final long job1Key = jobCreator.get().getKey();

    // when
    reprocessingTrigger.accept(this);

    final long job2Key = jobCreator.get().getKey();

    // then
    assertThat(job2Key).isGreaterThan(job1Key);
  }

  @Test
  public void shouldAssignUniqueIncidentKeyAfterRestart() {
    // given
    deploy(WORKFLOW_INCIDENT, "incident.bpmn");

    final long workflowInstanceKey = startWorkflowInstance("process").getWorkflowInstanceKey();
    assertIncidentCreated();

    // when
    reprocessingTrigger.accept(this);

    final long workflowInstanceKey2 = startWorkflowInstance("process").getWorkflowInstanceKey();

    // then
    final long firstIncidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .findFirst()
            .get()
            .getKey();

    final long secondIncidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey2)
            .findFirst()
            .get()
            .getKey();

    assertThat(firstIncidentKey).isLessThan(secondIncidentKey);
  }

  @Test
  public void shouldAssignUniqueDeploymentKeyAfterRestart() {
    // given
    final long deployment1Key =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .send()
            .join()
            .getKey();

    clientRule.waitUntilDeploymentIsDone(deployment1Key);
    // when
    reprocessingTrigger.accept(this);

    final long deployment2Key =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW_INCIDENT, "incident.bpmn")
            .send()
            .join()
            .getKey();

    // then
    assertThat(deployment2Key).isGreaterThan(deployment1Key);
  }

  @Test
  public void shouldCorrelateMessageAfterRestartIfEnteredBeforeA() throws Exception {
    // given
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW_MESSAGE, "message.bpmn")
        .send()
        .join();

    final long workflowInstanceKey =
        startWorkflowInstance("process", singletonMap("orderId", "order-123"))
            .getWorkflowInstanceKey();

    assertElementActivated("catch-event");

    reprocessingTrigger.accept(this);

    // when
    publishMessage("order canceled", "order-123", singletonMap("foo", "bar"));

    // then
    assertElementCompleted("process", "catch-event");
    assertWorkflowInstanceCompleted(
        "process",
        (workflowInstance) -> {
          assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
          assertThat(workflowInstance.getPayloadAsMap())
              .containsOnly(entry("orderId", "order-123"), entry("foo", "bar"));
        });
  }

  @Test
  public void shouldCorrelateMessageAfterRestartIfPublishedBefore() throws Exception {
    // given
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(WORKFLOW_MESSAGE, "message.bpmn")
        .send()
        .join();

    publishMessage("order canceled", "order-123", singletonMap("foo", "bar"));
    reprocessingTrigger.accept(this);

    // when
    final long workflowInstanceKey =
        startWorkflowInstance("process", singletonMap("orderId", "order-123"))
            .getWorkflowInstanceKey();
    assertElementCompleted("process", "catch-event");

    // then
    assertWorkflowInstanceCompleted(
        "process",
        (workflowInstance) -> {
          assertThat(workflowInstance.getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
          assertThat(workflowInstance.getPayloadAsMap())
              .containsOnly(entry("orderId", "order-123"), entry("foo", "bar"));
        });
  }

  private WorkflowInstanceEvent startWorkflowInstance(final String bpmnProcessId) {
    return clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .send()
        .join();
  }

  protected WorkflowInstanceEvent startWorkflowInstance(
      final String bpmnProcessId, final Map<String, Object> payload) {
    return clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .payload(payload)
        .send()
        .join();
  }

  protected void publishMessage(
      final String messageName, final String correlationKey, final Map<String, Object> payload) {
    clientRule
        .getWorkflowClient()
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .payload(payload)
        .send()
        .join();
  }

  protected void deleteSnapshotsAndRestart() {
    deleteSnapshotsAndRestart(() -> {});
  }

  protected void deleteSnapshotsAndRestart(final Runnable onStop) {
    brokerRule.getBroker().getBrokerContext().getBrokerConfiguration().getData().getDirectories();

    brokerRule.stopBroker();

    // delete snapshot files to trigger recovery
    try {
      brokerRule.purgeSnapshots();
    } catch (final Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    onStop.run();

    brokerRule.startBroker();
  }

  protected void restartBroker() {
    restartBroker(() -> {});
  }

  protected void restartBroker(final Runnable onStop) {
    brokerRule.stopBroker();

    onStop.run();

    brokerRule.startBroker();
  }

  private void deploy(final BpmnModelInstance workflowTwoTasks, final String s) {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(workflowTwoTasks, s)
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }
}
