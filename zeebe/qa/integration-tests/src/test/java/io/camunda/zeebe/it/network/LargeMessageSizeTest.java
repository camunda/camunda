/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.network;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.client.api.worker.JobWorkerBuilderStep1.JobWorkerBuilderStep3;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.util.ByteValue;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.springframework.util.unit.DataSize;

public final class LargeMessageSizeTest {

  private static final JobHandler COMPLETING_JOB_HANDLER =
      (client, job) -> {
        client.newCompleteCommand(job.getKey()).send().join();
      };
  private static final DataSize MAX_MESSAGE_SIZE = DataSize.ofMegabytes(5);
  // only use half of the max message size because some commands produce two events
  private static final long LARGE_SIZE = ByteValue.ofMegabytes(1);
  private static final long METADATA_SIZE = 512;

  private static final String LARGE_TEXT = "x".repeat((int) (LARGE_SIZE - METADATA_SIZE));

  private static final EmbeddedBrokerRule BROKER_RULE =
      new EmbeddedBrokerRule(b -> b.getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE));
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  private static BpmnModelInstance process(final String jobType) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(jobType))
        .endEvent()
        .done();
  }

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldDeployLargeProcess() {
    // given
    final var processAsString = Bpmn.convertToString(process(jobType));
    final var additionalChars = "<!--" + LARGE_TEXT + "-->";
    final var largeProcess = processAsString + additionalChars;

    // when
    final var deployment =
        CLIENT_RULE
            .getClient()
            .newDeployResourceCommand()
            .addResourceStringUtf8(largeProcess, "process.bpmn")
            .send()
            .join();

    final var processDefinitionKey = deployment.getProcesses().get(0).getProcessDefinitionKey();

    // then
    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    ZeebeAssertHelper.assertProcessInstanceCreated(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  public void shouldCreateInstanceWithLargeVariables() {
    // given
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process(jobType));

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .variables(largeVariables)
            .send()
            .join();

    // then
    ZeebeAssertHelper.assertProcessInstanceCreated(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  public void shouldCompleteJobWithLargeVariables() {
    // given
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process(jobType));

    final var processInstanceEvent =
        CLIENT_RULE
            .getClient()
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join();

    // when
    final Map<String, Object> largeVariables = Map.of("largeVariable", LARGE_TEXT);

    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(jobType)
        .handler(
            ((client, job) ->
                client.newCompleteCommand(job.getKey()).variables(largeVariables).send().join()))
        .open();

    // then
    ZeebeAssertHelper.assertProcessInstanceCompleted(processInstanceEvent.getProcessInstanceKey());
  }

  @Test
  public void shouldActivateJobsByRespectingMaxMessageSize() {
    // given
    final var modelInstance =
        Bpmn.createExecutableProcess("foo")
            .startEvent()
            .serviceTask()
            .zeebeJobType("foo")
            .endEvent()
            .done();

    CLIENT_RULE
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, "foo.bpmn")
        .send()
        .join();

    final var byteArray = new byte[1024 * 1024]; // 1 MB
    new Random().nextBytes(byteArray);
    final var message = new String(byteArray, StandardCharsets.UTF_8);

    final int numberOfJobsToActivate = 5;
    for (int i = 0; i < numberOfJobsToActivate; i++) {
      CLIENT_RULE
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
        CLIENT_RULE.getClient().newWorker().jobType("foo").handler(COMPLETING_JOB_HANDLER);

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

  @Test
  public void
      shouldActivateJobsByRespectingMaxMessageSizeWhenActualSizeIsBiggerThanMaxMessageSize() {
    // Numbers to test ResponseMapper.toActivateJobsResponse() method's if check where the actual
    // response size is bigger than the max message size when the response is built with metadata.
    // When we set jobVariableSize to 144, it produces ActivatedJob of size 1020 bytes.
    // 5 jobs of 1020 bytes equals to 5100 bytes (the configured maxMessageSize).
    // But when we build the actual response it exceeds 5100 bytes and fall into our case.
    final DataSize maxMessageSize = DataSize.ofBytes(5100);
    final int jobVariableSize = 144;

    BROKER_RULE.getBrokerCfg().getGateway().getNetwork().setMaxMessageSize(maxMessageSize);
    BROKER_RULE.restartBroker();

    // given
    final var modelInstance =
        Bpmn.createExecutableProcess("foo")
            .startEvent()
            .serviceTask()
            .zeebeJobType("foo")
            .endEvent()
            .done();

    CLIENT_RULE
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(modelInstance, "foo.bpmn")
        .send()
        .join();

    final var byteArray = new byte[jobVariableSize];
    final var message = new String(byteArray, StandardCharsets.UTF_8);

    final int numberOfJobsToActivate = 5;
    for (int i = 0; i < numberOfJobsToActivate; i++) {
      CLIENT_RULE
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
        CLIENT_RULE.getClient().newWorker().jobType("foo").handler(COMPLETING_JOB_HANDLER);

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

    // reset max message size to the initial value
    BROKER_RULE.getBrokerCfg().getGateway().getNetwork().setMaxMessageSize(MAX_MESSAGE_SIZE);
    BROKER_RULE.restartBroker();
  }
}
