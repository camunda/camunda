/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.worker.JobHandler;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.it.cluster.clustering.ClusteringRule;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

/**
 * Separate from {@link LargeMessageSizeTest} because the gateway max message size is fixed when the
 * {@link ClusteringRule} starts.
 */
public final class JobActivationMaxMessageSizeTest {

  private static final JobHandler COMPLETING_JOB_HANDLER =
      (client, job) -> {
        client.newCompleteCommand(job.getKey()).send().join();
      };
  private static final DataSize BROKER_MAX_MESSAGE_SIZE = DataSize.ofMegabytes(5);
  private static final DataSize GATEWAY_MAX_MESSAGE_SIZE = DataSize.ofBytes(5100);

  private final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          1,
          1,
          b -> b.getNetwork().setMaxMessageSize(BROKER_MAX_MESSAGE_SIZE),
          g -> g.getNetwork().setMaxMessageSize(GATEWAY_MAX_MESSAGE_SIZE));
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);
  @Rule public RuleChain ruleChain = RuleChain.outerRule(clusteringRule).around(clientRule);

  @Test
  public void shouldActivateJobsWhenActualSizeIsBiggerThanMaxMessageSize() {
    // Numbers to test ResponseMapper.toActivateJobsResponse() method's if check where the actual
    // response size is bigger than the max message size when the response is built with metadata.
    // When we set jobVariableSize to 144, it produces ActivatedJob of size 1020 bytes.
    // 5 jobs of 1020 bytes equals to 5100 bytes (the configured maxMessageSize).
    // But when we build the actual response it exceeds 5100 bytes and fall into our case.
    final int jobVariableSize = 144;

    // given
    final var modelInstance =
        Bpmn.createExecutableProcess("foo")
            .startEvent()
            .serviceTask()
            .zeebeJobType("foo")
            .endEvent()
            .done();

    clientRule
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, "foo.bpmn")
        .send()
        .join();

    final var byteArray = new byte[jobVariableSize];
    final var message = new String(byteArray, StandardCharsets.UTF_8);

    final int numberOfJobsToActivate = 5;
    for (int i = 0; i < numberOfJobsToActivate; i++) {
      clientRule
          .getClient()
          .newCreateInstanceCommand()
          .bpmnProcessId("foo")
          .latestVersion()
          .variables(Map.of("message_content", message))
          .send();
    }

    Assertions.assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withType("foo")
                .limit(numberOfJobsToActivate))
        .describedAs("Expect that all jobs are created.")
        .hasSize(numberOfJobsToActivate);

    // when
    final JobWorkerBuilderStep3 builder =
        clientRule.getClient().newWorker().jobType("foo").handler(COMPLETING_JOB_HANDLER);

    // then
    try (final JobWorker ignored = builder.open()) {
      Awaitility.await("until all jobs are completed")
          .pollInterval(Duration.ofMillis(100))
          .atMost(Duration.ofSeconds(5))
          .untilAsserted(
              () ->
                  assertThat(
                          RecordingExporter.jobRecords(JobIntent.COMPLETED)
                              .withType("foo")
                              .limit(numberOfJobsToActivate)
                              .count())
                      .isEqualTo(numberOfJobsToActivate));
    }
  }
}
