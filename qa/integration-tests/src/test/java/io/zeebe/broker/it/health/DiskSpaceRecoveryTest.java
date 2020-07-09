/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.zeebe.broker.it.util.GrpcClientRule;
import io.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.value.JobRecordValueAssert;
import io.zeebe.protocol.record.value.TimerRecordValueAssert;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.springframework.util.unit.DataSize;

public class DiskSpaceRecoveryTest {
  private final Timeout testTimeout = Timeout.seconds(120);
  private final EmbeddedBrokerRule embeddedBrokerRule =
      new EmbeddedBrokerRule(
          cfg -> {
            cfg.getData().setDiskUsageMonitoringInterval(Duration.ofSeconds(1));
          });
  private final GrpcClientRule clientRule = new GrpcClientRule(embeddedBrokerRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(embeddedBrokerRule).around(clientRule);

  @Test
  public void shouldStopAcceptingRequestWhenDiskSpaceFull() throws InterruptedException {
    // given
    waitUntilDiskSpaceNotAvailable();

    // when
    final var resultFuture =
        clientRule
            .getClient()
            .newPublishMessageCommand()
            .messageName("test")
            .correlationKey("test")
            .send();

    // then
    Assertions.assertThatThrownBy(resultFuture::join)
        .hasRootCauseMessage(
            "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
  }

  @Test
  public void shouldRestartAcceptingRequestWhenDiskSpaceAvailableAgain()
      throws InterruptedException {
    // given
    waitUntilDiskSpaceNotAvailable();

    // when
    waitUntilDiskSpaceAvailable();
    final var resultFuture =
        clientRule
            .getClient()
            .newPublishMessageCommand()
            .messageName("test")
            .correlationKey("Test")
            .send();

    // then
    assertThatCode(resultFuture::join).doesNotThrowAnyException();
  }

  @Test
  public void shouldProcessTimersWhenDiskSpaceAvailableAgain() throws InterruptedException {
    // given
    final BpmnModelInstance timerWorkflow =
        Bpmn.createExecutableProcess("TimerProcess")
            .startEvent("start")
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT100S"))
            .endEvent("end")
            .done();
    final long workflowKey = clientRule.deployWorkflow(timerWorkflow);
    final long workflowInstanceKey = clientRule.createWorkflowInstance(workflowKey);
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.timerRecords(TimerIntent.CREATED)
                    .withWorkflowInstanceKey(workflowInstanceKey)
                    .limit(1)
                    .exists());

    final var timerKey =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    waitUntilDiskSpaceNotAvailable();
    embeddedBrokerRule.getClock().addTime(Duration.ofSeconds(100));

    waitUntilDiskSpaceAvailable();

    // then
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.timerRecords(TimerIntent.TRIGGER)
                    .withElementInstanceKey(timerKey)
                    .limit(1)
                    .exists());

    TimerRecordValueAssert.assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGER).getFirst().getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldTimeoutActivatedJobsWhenDiskSpaceAvailableAgain() throws InterruptedException {
    // given
    final BpmnModelInstance timerWorkflow =
        Bpmn.createExecutableProcess("TimerProcess")
            .startEvent("start")
            .serviceTask("test", s -> s.zeebeJobType("timeout"))
            .endEvent("end")
            .done();
    final long workflowKey = clientRule.deployWorkflow(timerWorkflow);
    final long workflowInstanceKey = clientRule.createWorkflowInstance(workflowKey);
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.jobRecords(JobIntent.CREATED)
                    .withWorkflowInstanceKey(workflowInstanceKey)
                    .limit(1)
                    .exists());

    final var jobs =
        clientRule
            .getClient()
            .newActivateJobsCommand()
            .jobType("timeout")
            .maxJobsToActivate(1)
            .timeout(Duration.ofSeconds(60))
            .workerName("test")
            .send()
            .join()
            .getJobs();

    assertThat(jobs).isNotEmpty();

    final var jobKey = jobs.get(0).getElementInstanceKey();

    // when
    waitUntilDiskSpaceNotAvailable();
    embeddedBrokerRule.getClock().addTime(Duration.ofSeconds(100));

    waitUntilDiskSpaceAvailable();

    // then
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.jobRecords(JobIntent.TIME_OUT)
                    .withWorkflowInstanceKey(workflowInstanceKey)
                    .limit(1)
                    .exists());

    JobRecordValueAssert.assertThat(
            RecordingExporter.jobRecords(JobIntent.TIME_OUT).getFirst().getValue())
        .hasElementInstanceKey(jobKey);
  }

  private void waitUntilDiskSpaceNotAvailable() throws InterruptedException {
    final var diskSpaceMonitor = embeddedBrokerRule.getBroker().getDiskSpaceUsageMonitor();

    final CountDownLatch diskSpaceNotAvailable = new CountDownLatch(1);
    diskSpaceMonitor.addDiskUsageListener(
        new DiskSpaceUsageListener() {
          @Override
          public void onDiskSpaceNotAvailable() {
            diskSpaceNotAvailable.countDown();
          }

          @Override
          public void onDiskSpaceAvailable() {}
        });

    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> DataSize.ofGigabytes(0).toBytes());

    embeddedBrokerRule.getClock().addTime(Duration.ofSeconds(1));

    // when
    assertThat(diskSpaceNotAvailable.await(2, TimeUnit.SECONDS)).isTrue();
  }

  private void waitUntilDiskSpaceAvailable() throws InterruptedException {
    final var diskSpaceMonitor = embeddedBrokerRule.getBroker().getDiskSpaceUsageMonitor();
    final CountDownLatch diskSpaceAvailableAgain = new CountDownLatch(1);
    diskSpaceMonitor.addDiskUsageListener(
        new DiskSpaceUsageListener() {
          @Override
          public void onDiskSpaceAvailable() {
            diskSpaceAvailableAgain.countDown();
          }
        });

    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> DataSize.ofGigabytes(100).toBytes());
    embeddedBrokerRule.getClock().addTime(Duration.ofSeconds(1));
    assertThat(diskSpaceAvailableAgain.await(2, TimeUnit.SECONDS)).isTrue();
  }
}
