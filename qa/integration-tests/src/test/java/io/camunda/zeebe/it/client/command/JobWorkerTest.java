/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.broker.test.EmbeddedBrokerRule;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import java.time.Instant;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public final class JobWorkerTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final GrpcClientRule CLIENT_RULE = new GrpcClientRule(BROKER_RULE);

  @ClassRule
  public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(CLIENT_RULE);

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  private String jobType;

  @Before
  public void init() {
    jobType = helper.getJobType();
  }

  @Test
  public void shouldActivateJob() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeJobRetries("5")
                        .zeebeTaskHeader("x", "1")
                        .zeebeTaskHeader("y", "2"))
            .done();
    final var processDefinitionKey = CLIENT_RULE.deployProcess(process);

    final var processInstanceKey =
        CLIENT_RULE.createProcessInstance(processDefinitionKey, "{\"a\":1, \"b\":2}");

    // when
    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    CLIENT_RULE.getClient().newWorker().jobType(jobType).handler(jobHandler).name("test").open();

    waitUntil(() -> jobHandler.getHandledJobs().size() >= 1);

    // then
    final ActivatedJob job = jobHandler.getHandledJobs().get(0);

    assertThat(job.getType()).isEqualTo(jobType);
    assertThat(job.getRetries()).isEqualTo(5);
    assertThat(job.getDeadline()).isGreaterThan(Instant.now().toEpochMilli());
    assertThat(job.getWorker()).isEqualTo("test");
    assertThat(job.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(job.getBpmnProcessId()).isEqualTo("process");
    assertThat(job.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(job.getElementId()).isEqualTo("task");
    assertThat(job.getCustomHeaders()).containsExactly(entry("x", "1"), entry("y", "2"));
    assertThat(job.getVariablesAsMap()).containsExactly(entry("a", 1), entry("b", 2));
  }

  @Test
  public void shouldActivateJobsOfDifferentTypes() {
    // given
    final long jobX = CLIENT_RULE.createSingleJob(jobType + "x");
    final long jobY = CLIENT_RULE.createSingleJob(jobType + "y");

    // when
    final RecordingJobHandler jobHandlerX = new RecordingJobHandler();
    final RecordingJobHandler jobHandlerY = new RecordingJobHandler();

    CLIENT_RULE.getClient().newWorker().jobType(jobType + "x").handler(jobHandlerX).open();
    CLIENT_RULE.getClient().newWorker().jobType(jobType + "y").handler(jobHandlerY).open();

    waitUntil(() -> jobHandlerX.getHandledJobs().size() >= 1);
    waitUntil(() -> jobHandlerY.getHandledJobs().size() >= 1);

    // then
    assertThat(jobHandlerX.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobX);

    assertThat(jobHandlerY.getHandledJobs())
        .hasSize(1)
        .extracting(ActivatedJob::getKey)
        .contains(jobY);
  }

  @Test
  public void shouldFetchOnlySpecifiedVariables() {
    // given
    CLIENT_RULE.createSingleJob(jobType, b -> {}, "{\"a\":1, \"b\":2, \"c\":3,\"d\":4}");

    // when
    final List<String> fetchVariables = List.of("a", "b");

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    CLIENT_RULE
        .getClient()
        .newWorker()
        .jobType(jobType)
        .handler(jobHandler)
        .fetchVariables(fetchVariables)
        .open();

    waitUntil(() -> jobHandler.getHandledJobs().size() >= 1);

    // then
    final ActivatedJob job = jobHandler.getHandledJobs().get(0);
    assertThat(job.getVariablesAsMap().keySet()).containsOnlyElementsOf(fetchVariables);
  }
}
