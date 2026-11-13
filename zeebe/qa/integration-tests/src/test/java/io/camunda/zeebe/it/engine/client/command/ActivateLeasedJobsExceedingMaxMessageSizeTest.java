/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * The gateway gives back (FAILs) jobs it activated but cannot deliver. For a leased job the broker
 * only accepts that FAIL if it carries the job lease token.
 *
 * <p>The max message size give-back is forced with control characters: they take 1 byte in the
 * broker's msgpack, so the job fits the broker's job batch, but Jackson escapes each as the 6 byte
 * {@code \u0001} in the gateway's JSON, so the job exceeds the gateway's max message size. Only
 * gRPC is covered, as the REST mapper sizes jobs by their msgpack length and never defers them
 * here.
 */
@ZeebeIntegration
public class ActivateLeasedJobsExceedingMaxMessageSizeTest {

  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofKilobytes(32);

  // 5 variables of 2000 control characters each:
  // - one setVariables request is ~12 KB as JSON, well below the 32 KB limit
  // - the job variables take ~10 KB as msgpack, well below the 32 KB broker batch limit
  // - the job variables take ~60 KB as JSON, well above the 32 KB gateway limit
  private static final int VARIABLE_COUNT = 5;
  private static final String CONTROL_CHARACTERS = "\u0001".repeat(2000);

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withUnifiedConfig(c -> c.getCluster().getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE));

  @AutoClose CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClient() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  public void shouldGiveBackLeasedJobExceedingMaxMessageSize() {
    // given
    final var jobType = "job-" + UUID.randomUUID();
    final var processDefinitionKey =
        resourcesHelper.deployProcess(
            Bpmn.createExecutableProcess("process-" + UUID.randomUUID())
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done());
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    IntStream.range(0, VARIABLE_COUNT)
        .forEach(
            i ->
                client
                    .newSetVariablesCommand(processInstanceKey)
                    .variables(Map.of("control-" + i, CONTROL_CHARACTERS))
                    .send()
                    .join());
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .limitByCount(r -> true, VARIABLE_COUNT)
                .count())
        .describedAs("Expected all variables to be created")
        .isEqualTo(VARIABLE_COUNT);

    // when the gateway activates the job but cannot deliver it, so it gives the job back
    client
        .newActivateJobsCommand()
        .useGrpc()
        .jobType(jobType)
        .maxJobsToActivate(1)
        .timeout(Duration.ofMinutes(14))
        .withLease(true)
        .requestTimeout(Duration.ofSeconds(2))
        .send()
        .toCompletableFuture()
        .exceptionally(t -> null)
        .join();

    // then the broker accepts the give-back
    final var jobKey =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();
    final var giveBackRecords =
        RecordingExporter.jobRecords()
            .withRecordKey(jobKey)
            .limit(r -> r.getIntent() == JobIntent.FAILED || isRejectedFail(r))
            .toList();
    final var giveBackOutcome = giveBackRecords.getLast();

    assertThat(giveBackOutcome)
        .describedAs("Expected the broker to accept the give-back FAIL, but it was rejected")
        .satisfies(r -> assertThat(r.getRecordType()).isEqualTo(RecordType.EVENT))
        .satisfies(r -> assertThat(r.getIntent()).isEqualTo(JobIntent.FAILED));
    assertThat(((JobRecordValue) giveBackOutcome.getValue()).getErrorMessage())
        .contains("the response is bigger than the maximum allowed message size");
  }

  private static boolean isRejectedFail(final Record<?> record) {
    return record.getIntent() == JobIntent.FAIL
        && record.getRecordType() == RecordType.COMMAND_REJECTION;
  }
}
