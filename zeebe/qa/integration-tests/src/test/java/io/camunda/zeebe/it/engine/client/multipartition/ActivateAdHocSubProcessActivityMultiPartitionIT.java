/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.multipartition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Covers activating activities of ad-hoc sub-processes that live on different partitions. Only the
 * partition owning an ad-hoc sub-process instance knows it, so the gateway has to send the command
 * there; any other partition rejects it as not found.
 *
 * <p>An engine test runs a single partition, so a command sent to the wrong one cannot happen
 * there.
 */
@ZeebeIntegration
final class ActivateAdHocSubProcessActivityMultiPartitionIT {

  private static final int PARTITION_COUNT = 3;

  /** A multiple of the partition count, so the gateway's round robin reaches every partition. */
  private static final int INSTANCES = 2 * PARTITION_COUNT;

  private static final String AD_HOC_SUB_PROCESS_ID = "ad-hoc";
  private static final String ACTIVITY_ID = "activity";

  @TestZeebe(partitionCount = PARTITION_COUNT)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnauthenticatedAccess()
          .withClusterConfig(cluster -> cluster.setPartitionCount(PARTITION_COUNT));

  @AutoClose
  private final CamundaClient client =
      BROKER.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();

  private final String processId = Strings.newRandomValidBpmnId();
  private final String jobType = Strings.newRandomValidBpmnId();

  @BeforeEach
  void setUp() {
    RecordingExporter.setMaximumWaitTime(Duration.ofSeconds(30).toMillis());
  }

  @Test
  void shouldActivateActivitiesOfAdHocSubProcessesOnEveryPartition() {
    // given
    new ZeebeResourcesHelper(client)
        .deployProcess(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .adHocSubProcess(
                    AD_HOC_SUB_PROCESS_ID,
                    adHocSubProcess ->
                        adHocSubProcess.serviceTask(
                            ACTIVITY_ID, activity -> activity.zeebeJobType(jobType)))
                .endEvent()
                .done());

    final List<Long> processInstanceKeys =
        IntStream.range(0, INSTANCES)
            .mapToObj(
                i ->
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(processId)
                        .latestVersion()
                        .send()
                        .join()
                        .getProcessInstanceKey())
            .toList();
    final List<Long> adHocSubProcessKeys =
        processInstanceKeys.stream().map(this::awaitAdHocSubProcessKey).toList();

    assertThat(adHocSubProcessKeys.stream().map(Protocol::decodePartitionId).distinct())
        .as("the ad-hoc sub-processes are spread over every partition")
        .hasSize(PARTITION_COUNT);

    // when - then
    // Each ad-hoc sub-process is activated once per partition: a request that is not addressed to a
    // partition is sent to the next one in turn, so these calls reach a partition that does not own
    // the ad-hoc sub-process at least once, wherever the rotation starts. Activating just once
    // could land on the owning partition by coincidence for every ad-hoc sub-process.
    for (final long adHocSubProcessKey : adHocSubProcessKeys) {
      for (int i = 0; i < PARTITION_COUNT; i++) {
        assertThatCode(() -> activateActivity(adHocSubProcessKey))
            .as(
                "activating an activity of ad-hoc sub-process %d on partition %d",
                adHocSubProcessKey, Protocol.decodePartitionId(adHocSubProcessKey))
            .doesNotThrowAnyException();
      }
    }

    // and - every activation took effect on the partition owning the ad-hoc sub-process
    final int activations = INSTANCES * PARTITION_COUNT;
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType(jobType)
                .limit(activations)
                .count())
        .isEqualTo(activations);
  }

  private long awaitAdHocSubProcessKey(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId(AD_HOC_SUB_PROCESS_ID)
        .getFirst()
        .getKey();
  }

  private void activateActivity(final long adHocSubProcessKey) {
    client
        .newActivateAdHocSubProcessActivitiesCommand(String.valueOf(adHocSubProcessKey))
        .activateElements(ACTIVITY_ID)
        .send()
        .join();
  }
}
