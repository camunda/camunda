/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValueAssert;
import io.camunda.zeebe.protocol.record.value.TimerRecordValueAssert;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.util.unit.DataSize;

@ZeebeIntegration
@Timeout(120)
final class DiskSpaceMonitorIT {

  // the test leaves the broker without disk space, so every test needs its own broker
  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(
              cfg -> {
                cfg.getSystem().setClockControlled(true);
                cfg.getData()
                    .getPrimaryStorage()
                    .getDisk()
                    .setMonitoringInterval(Duration.ofSeconds(1));
              });

  private CamundaClient client;
  private ZeebeResourcesHelper resourcesHelper;
  private ActorClockActuator clock;

  @BeforeEach
  void createClient() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(10)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
    clock = ActorClockActuator.of(zeebe);
  }

  @AfterEach
  void closeClient() {
    client.close();
  }

  @Test
  void shouldStopAcceptingRequestWhenDiskSpaceFull() throws InterruptedException {
    // given
    waitUntilDiskSpaceNotAvailable();

    // when
    final var resultFuture =
        client.newPublishMessageCommand().messageName("test").correlationKey("test").send();

    // then
    Assertions.assertThatThrownBy(resultFuture::join)
        .hasRootCauseMessage(
            "RESOURCE_EXHAUSTED: Cannot accept requests for partition 1. Broker is out of disk space");
  }

  @Test
  void shouldRestartAcceptingRequestWhenDiskSpaceAvailableAgain() throws InterruptedException {
    // given
    waitUntilDiskSpaceNotAvailable();

    // when
    waitUntilDiskSpaceAvailable();
    final var resultFuture =
        client.newPublishMessageCommand().messageName("test").correlationKey("Test").send();

    // then
    assertThatCode(resultFuture::join).doesNotThrowAnyException();
  }

  @Test
  void shouldProcessTimersWhenDiskSpaceAvailableAgain() throws InterruptedException {
    // given
    final BpmnModelInstance timerProcess =
        Bpmn.createExecutableProcess("TimerProcess")
            .startEvent("start")
            .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT100S"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(timerProcess);
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.timerRecords(TimerIntent.CREATED)
                    .withProcessInstanceKey(processInstanceKey)
                    .limit(1)
                    .exists());

    final var timerKey =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getValue()
            .getElementInstanceKey();

    // when
    waitUntilDiskSpaceNotAvailable();
    clock.addTime(new AddTimeRequest(Duration.ofSeconds(100).toMillis()));

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
        .hasProcessInstanceKey(processInstanceKey);
  }

  @Test
  void shouldTimeoutActivatedJobsWhenDiskSpaceAvailableAgain() throws InterruptedException {
    // given
    final BpmnModelInstance timerProcess =
        Bpmn.createExecutableProcess("TimerProcess")
            .startEvent("start")
            .serviceTask("test", s -> s.zeebeJobType("timeout"))
            .endEvent("end")
            .done();
    final long processDefinitionKey = resourcesHelper.deployProcess(timerProcess);
    final long processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () ->
                RecordingExporter.jobRecords(JobIntent.CREATED)
                    .withProcessInstanceKey(processInstanceKey)
                    .limit(1)
                    .exists());

    final var jobs =
        client
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
    clock.addTime(new AddTimeRequest(Duration.ofSeconds(100).toMillis()));

    waitUntilDiskSpaceAvailable();

    // then
    Awaitility.await()
        .timeout(Duration.ofSeconds(60))
        .until(
            () -> {
              clock.addTime(new AddTimeRequest(Duration.ofMinutes(1).toMillis()));
              return RecordingExporter.jobRecords(JobIntent.TIME_OUT)
                  .withProcessInstanceKey(processInstanceKey)
                  .limit(1)
                  .exists();
            });

    JobRecordValueAssert.assertThat(
            RecordingExporter.jobRecords(JobIntent.TIME_OUT).getFirst().getValue())
        .hasElementInstanceKey(jobKey);
  }

  private void waitUntilDiskSpaceNotAvailable() throws InterruptedException {
    final var diskSpaceMonitor =
        zeebe.bean(Broker.class).getBrokerContext().getDiskSpaceUsageMonitor();

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

    clock.addTime(new AddTimeRequest(Duration.ofSeconds(1).toMillis()));

    // when
    assertThat(diskSpaceNotAvailable.await(2, TimeUnit.SECONDS)).isTrue();
  }

  private void waitUntilDiskSpaceAvailable() throws InterruptedException {
    final var diskSpaceMonitor =
        zeebe.bean(Broker.class).getBrokerContext().getDiskSpaceUsageMonitor();
    final CountDownLatch diskSpaceAvailableAgain = new CountDownLatch(1);
    diskSpaceMonitor.addDiskUsageListener(
        new DiskSpaceUsageListener() {
          @Override
          public void onDiskSpaceAvailable() {
            diskSpaceAvailableAgain.countDown();
          }
        });

    diskSpaceMonitor.setFreeDiskSpaceSupplier(() -> DataSize.ofGigabytes(100).toBytes());
    clock.addTime(new AddTimeRequest(Duration.ofSeconds(1).toMillis()));
    assertThat(diskSpaceAvailableAgain.await(2, TimeUnit.SECONDS)).isTrue();
  }
}
