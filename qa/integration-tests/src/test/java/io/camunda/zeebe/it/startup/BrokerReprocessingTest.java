/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.startup;

import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.Assert.fail;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.engine.processing.job.JobTimeoutTrigger;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.sched.clock.ControlledActorClock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class BrokerReprocessingTest {

  private static final String PROCESS_ID = "process";
  private static final String NULL_VARIABLES = "{}";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeJobType("foo"))
          .endEvent("end")
          .done();
  private static final BpmnModelInstance PROCESS_TWO_TASKS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task1", t -> t.zeebeJobType("foo"))
          .serviceTask("task2", t -> t.zeebeJobType("bar"))
          .endEvent("end")
          .done();
  private static final BpmnModelInstance PROCESS_INCIDENT =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .serviceTask("task", t -> t.zeebeJobType("test").zeebeInputExpression("foo", "foo"))
          .endEvent("end")
          .done();
  private static final BpmnModelInstance PROCESS_MESSAGE =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(m -> m.name("order canceled").zeebeCorrelationKeyExpression("orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();
  private static final BpmnModelInstance PROCESS_TIMER =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .endEvent()
          .done();

  @Parameter(0)
  public Consumer<BrokerReprocessingTest> reprocessingTrigger;

  @Parameter(1)
  public String name;

  public final EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public final GrpcClientRule clientRule = new GrpcClientRule(brokerRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);
  @Rule public ExpectedException exception = ExpectedException.none();
  @Rule public Timeout timeout = new Timeout(120, TimeUnit.SECONDS);
  private Runnable restartAction = () -> {};

  @Parameters(name = "{index}: {1}")
  public static Object[][] reprocessingTriggers() {
    return new Object[][] {
      new Object[] {
        (Consumer<BrokerReprocessingTest>) BrokerReprocessingTest::restartBroker, "restart"
      },
      new Object[] {
        (Consumer<BrokerReprocessingTest>) BrokerReprocessingTest::deleteSnapshotsAndRestart,
        "restart-without-snapshot"
      }
    };
  }

  @Test
  public void shouldDirectlyRestart() {
    // given

    // when
    reprocessingTrigger.accept(this);

    // then - no error
  }

  @Test
  public void shouldCreateProcessInstanceAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertProcessInstanceCreated();
  }

  @Test
  public void shouldContinueProcessInstanceAtTaskAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join();

    final var jobFoo = RecordingExporter.jobRecords(JobIntent.CREATED).withType("foo").getFirst();

    // when
    reprocessingTrigger.accept(this);
    completeJobWithKey(jobFoo.getKey());

    // then
    ZeebeAssertHelper.assertJobCompleted();
    ZeebeAssertHelper.assertProcessInstanceCompleted(PROCESS_ID);
  }

  @Test
  public void shouldContinueProcessInstanceWithLockedTaskAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join();

    final var activatedJobFoo = activateJob("foo");

    // when
    reprocessingTrigger.accept(this);

    awaitGateway();
    clientRule.getClient().newCompleteCommand(activatedJobFoo.getKey()).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted();
    ZeebeAssertHelper.assertProcessInstanceCompleted(PROCESS_ID);
  }

  @Test
  public void shouldContinueProcessInstanceAtSecondTaskAfterRestart() throws Exception {
    // given
    deploy(PROCESS_TWO_TASKS, "two-tasks.bpmn");

    final Duration defaultJobTimeout =
        clientRule.getClient().getConfiguration().getDefaultJobTimeout();

    clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .send()
        .join();

    final var jobFoo = RecordingExporter.jobRecords(JobIntent.CREATED).withType("foo").getFirst();
    completeJobWithKey(jobFoo.getKey());

    RecordingExporter.jobRecords(JobIntent.CREATED).withType("bar").getFirst();
    var activatedJobBar = activateJob("bar");

    // when
    restartAction = () -> brokerRule.getClock().addTime(defaultJobTimeout);
    reprocessingTrigger.accept(this);

    awaitJobTimeout();
    activatedJobBar = activateJob("bar");
    completeJobWithKey(activatedJobBar.getKey());

    // then
    ZeebeAssertHelper.assertJobCompleted("foo");
    ZeebeAssertHelper.assertJobCompleted("bar");
    ZeebeAssertHelper.assertProcessInstanceCompleted(PROCESS_ID);
  }

  @Test
  public void shouldDeployNewProcessVersionAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    // when
    reprocessingTrigger.accept(this);

    final DeploymentEvent deploymentResult =
        clientRule
            .getClient()
            .newDeployCommand()
            .addProcessModel(PROCESS, "process-2.bpmn")
            .send()
            .join();

    // then
    assertThat(deploymentResult.getProcesses().get(0).getVersion()).isEqualTo(2);

    final ProcessInstanceEvent processInstanceV1 =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .version(1)
            .send()
            .join();

    final ProcessInstanceEvent processInstanceV2 =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    // then
    assertThat(processInstanceV1.getVersion()).isEqualTo(1);
    assertThat(processInstanceV2.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldNotReceiveLockedJobAfterRestart() {
    // given
    clientRule.createSingleJob("foo");
    activateJob("foo");

    // when
    reprocessingTrigger.accept(this);

    // then
    awaitGateway();
    final ActivateJobsResponse jobsResponse =
        clientRule
            .getClient()
            .newActivateJobsCommand()
            .jobType("foo")
            .maxJobsToActivate(10)
            .workerName("this")
            .send()
            .join();

    assertThat(jobsResponse.getJobs()).isEmpty();
  }

  @Test
  public void shouldReceiveLockExpiredJobAfterRestart() {
    // given
    clientRule.createSingleJob("foo");
    final Duration defaultJobTimeout =
        clientRule.getClient().getConfiguration().getDefaultJobTimeout();
    activateJob("foo");

    // when
    restartAction = () -> brokerRule.getClock().addTime(defaultJobTimeout);
    reprocessingTrigger.accept(this);
    awaitJobTimeout();
    final var activatedJob = activateJob("foo");

    // then
    completeJobWithKey(activatedJob.getKey());
    ZeebeAssertHelper.assertJobCompleted();
  }

  @Test
  public void shouldResolveIncidentAfterRestart() {
    // given
    deploy(PROCESS_INCIDENT, "incident.bpmn");

    final ProcessInstanceEvent instanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    ZeebeAssertHelper.assertIncidentCreated();
    ZeebeAssertHelper.assertElementReady("task");

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    // when
    reprocessingTrigger.accept(this);

    clientRule
        .getClient()
        .newSetVariablesCommand(instanceEvent.getProcessInstanceKey())
        .variables("{\"foo\":\"bar\"}")
        .send()
        .join();

    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    ZeebeAssertHelper.assertIncidentResolved();
    ZeebeAssertHelper.assertJobCreated("test");
  }

  @Test
  public void shouldResolveFailedIncidentAfterRestart() {
    // given
    deploy(PROCESS_INCIDENT, "incident.bpmn");

    final ProcessInstanceEvent instanceEvent =
        clientRule
            .getClient()
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .send()
            .join();

    ZeebeAssertHelper.assertIncidentCreated();
    ZeebeAssertHelper.assertElementReady("task");

    Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    clientRule
        .getClient()
        .newSetVariablesCommand(instanceEvent.getProcessInstanceKey())
        .variables("{\"x\":\"y\"}")
        .send()
        .join();

    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    ZeebeAssertHelper.assertIncidentResolveFailed();

    // when
    reprocessingTrigger.accept(this);

    incident = RecordingExporter.incidentRecords(IncidentIntent.CREATED).getLast();

    clientRule
        .getClient()
        .newSetVariablesCommand(instanceEvent.getProcessInstanceKey())
        .variables("{\"foo\":\"bar\"}")
        .send()
        .join();

    clientRule.getClient().newResolveIncidentCommand(incident.getKey()).send().join();

    // then
    ZeebeAssertHelper.assertIncidentResolved();
    ZeebeAssertHelper.assertJobCreated("test");
  }

  @Test
  public void shouldAssignUniqueProcessInstanceKeyAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    final long processInstance1Key = startProcessInstance(PROCESS_ID).getProcessInstanceKey();

    // when
    reprocessingTrigger.accept(this);

    final long processInstance2Key = startProcessInstance(PROCESS_ID).getProcessInstanceKey();

    // then
    assertThat(processInstance2Key).isGreaterThan(processInstance1Key);
  }

  @Test
  public void shouldAssignUniqueJobKeyAfterRestart() {
    // given
    deploy(PROCESS, "process.bpmn");

    final Supplier<Long> jobCreator = () -> clientRule.createSingleJob("foo");

    final long job1Key = jobCreator.get();

    // when
    reprocessingTrigger.accept(this);

    final long job2Key = jobCreator.get();

    // then
    assertThat(job2Key).isGreaterThan(job1Key);
  }

  @Test
  public void shouldAssignUniqueIncidentKeyAfterRestart() {
    // given
    deploy(PROCESS_INCIDENT, "incident.bpmn");

    final long processInstanceKey = startProcessInstance(PROCESS_ID).getProcessInstanceKey();
    ZeebeAssertHelper.assertIncidentCreated();

    // when
    reprocessingTrigger.accept(this);

    final long processInstanceKey2 = startProcessInstance(PROCESS_ID).getProcessInstanceKey();

    // then
    final long firstIncidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    final long secondIncidentKey =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey2)
            .getFirst()
            .getKey();

    assertThat(firstIncidentKey).isLessThan(secondIncidentKey);
  }

  @Test
  public void shouldAssignUniqueDeploymentKeyAfterRestart() {
    // given
    final long deployment1Key =
        clientRule
            .getClient()
            .newDeployCommand()
            .addProcessModel(PROCESS_INCIDENT, "incident.bpmn")
            .send()
            .join()
            .getKey();

    clientRule.waitUntilDeploymentIsDone(deployment1Key);
    // when
    reprocessingTrigger.accept(this);

    final long deployment2Key =
        clientRule
            .getClient()
            .newDeployCommand()
            .addProcessModel(PROCESS_INCIDENT, "incident.bpmn")
            .send()
            .join()
            .getKey();

    // then
    assertThat(deployment2Key).isGreaterThan(deployment1Key);
  }

  @Test
  public void shouldCorrelateMessageAfterRestartIfEnteredBefore() throws Exception {
    // given
    deploy(PROCESS_MESSAGE, "message.bpmn");

    final long processInstanceKey =
        startProcessInstance(PROCESS_ID, singletonMap("orderId", "order-123"))
            .getProcessInstanceKey();

    assertThat(
            RecordingExporter.processMessageSubscriptionRecords(
                    ProcessMessageSubscriptionIntent.CREATED)
                .exists())
        .isTrue();

    reprocessingTrigger.accept(this);

    // when
    publishMessage("order canceled", "order-123", singletonMap("foo", "bar"));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("catch-event")
                .exists())
        .isTrue();

    ZeebeAssertHelper.assertProcessInstanceCompleted(processInstanceKey);
    assertThat(ProcessInstances.getCurrentVariables(processInstanceKey))
        .containsOnly(entry("foo", "\"bar\""), entry("orderId", "\"order-123\""));
  }

  @Test
  public void shouldCorrelateMessageAfterRestartIfPublishedBefore() throws Exception {
    // given
    deploy(PROCESS_MESSAGE, "message.bpmn");

    publishMessage("order canceled", "order-123", singletonMap("foo", "bar"));
    reprocessingTrigger.accept(this);

    // when
    final long processInstanceKey =
        startProcessInstance(PROCESS_ID, singletonMap("orderId", "order-123"))
            .getProcessInstanceKey();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("catch-event")
                .exists())
        .isTrue();

    // then
    ZeebeAssertHelper.assertProcessInstanceCompleted(processInstanceKey);
    assertThat(ProcessInstances.getCurrentVariables(processInstanceKey))
        .containsOnly(entry("foo", "\"bar\""), entry("orderId", "\"order-123\""));
  }

  @Test
  public void shouldTriggerTimerAfterRestart() {
    // given
    deploy(PROCESS_TIMER, "timer.bpmn");

    startProcessInstance(PROCESS_ID);

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).exists()).isTrue();

    // when
    restartAction = () -> brokerRule.getClock().addTime(Duration.ofSeconds(10));
    reprocessingTrigger.accept(this);

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("timer")
                .exists())
        .isTrue();
  }

  private ProcessInstanceEvent startProcessInstance(final String bpmnProcessId) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .send()
        .join();
  }

  protected ProcessInstanceEvent startProcessInstance(
      final String bpmnProcessId, final Map<String, Object> variables) {
    return clientRule
        .getClient()
        .newCreateInstanceCommand()
        .bpmnProcessId(bpmnProcessId)
        .latestVersion()
        .variables(variables)
        .send()
        .join();
  }

  protected void publishMessage(
      final String messageName, final String correlationKey, final Map<String, Object> variables) {
    clientRule
        .getClient()
        .newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .variables(variables)
        .send()
        .join();
  }

  private void completeJobWithKey(final long key) {
    clientRule.getClient().newCompleteCommand(key).variables(NULL_VARIABLES).send().join();
  }

  private ActivatedJob activateJob(final String jobType) {
    RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).await();
    return Awaitility.await("activateJob")
        .until(
            () ->
                clientRule
                    .getClient()
                    .newActivateJobsCommand()
                    .jobType(jobType)
                    .maxJobsToActivate(1)
                    .send()
                    .join()
                    .getJobs(),
            jobs -> !jobs.isEmpty())
        .stream()
        .findFirst()
        .orElseThrow();
  }

  protected void deleteSnapshotsAndRestart() {
    brokerRule.stopBroker();

    // delete snapshot files to trigger recovery
    try {
      brokerRule.purgeSnapshots();
    } catch (final Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    restartAction.run();

    brokerRule.startBroker();
  }

  protected void restartBroker() {
    brokerRule.stopBroker();

    restartAction.run();

    brokerRule.startBroker();
  }

  private void deploy(final BpmnModelInstance processTwoTasks, final String s) {
    final DeploymentEvent deploymentEvent =
        clientRule.getClient().newDeployCommand().addProcessModel(processTwoTasks, s).send().join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());
  }

  private void awaitGateway() {
    Awaitility.await("awaitGateway")
        .until(
            () -> {
              try {
                clientRule.getClient().newTopologyRequest().send().join();
                return true;
              } catch (final Exception e) {
                return false;
              }
            });
  }

  private void awaitJobTimeout() {
    final Duration defaultJobTimeout =
        clientRule.getClient().getConfiguration().getDefaultJobTimeout();

    final ControlledActorClock clock = brokerRule.getClock();
    final Duration pollingInterval =
        JobTimeoutTrigger.TIME_OUT_POLLING_INTERVAL
            // this shouldn't be needed but is caused by the fact hat on reprocessing without
            // a snapshot a new deadline is set for the job
            // https://github.com/zeebe-io/zeebe/issues/1800
            .plus(defaultJobTimeout);

    Awaitility.await("awaitJobTimeout")
        .until(
            () -> {
              clock.addTime(pollingInterval);
              // not using RecordingExporter.jobRecords cause it is blocking
              return RecordingExporter.getRecords().stream()
                  .filter(r -> r.getValueType() == ValueType.JOB)
                  .anyMatch(r -> r.getIntent() == JobIntent.TIMED_OUT);
            });
  }
}
