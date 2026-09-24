/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.Variable;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the variable redaction proof of concept (product-hub #3805).
 *
 * <p>Redaction is applied once in the broker's exporter director, before the record is handed to
 * any exporter, so it must hold for whichever secondary storage this run is configured with. The
 * counterpart assertion matters just as much: job activation reads the engine's live state rather
 * than secondary storage, so a worker must still receive the plain value it needs to do its work.
 */
@MultiDbTest
public class VariableRedactionIT {

  private static final String PROCESS_ID = "variable-redaction-poc";
  private static final String JOB_TYPE = "variable-redaction-poc-job";

  private static final String SENSITIVE_NAME = "sensitive_ssn";
  private static final String SENSITIVE_VALUE = "123-45-6789";
  private static final String SENSITIVE_OBJECT_NAME = "sensitive_card";
  private static final Map<String, String> SENSITIVE_OBJECT_VALUE =
      Map.of("pan", "4111111111111111");
  private static final String PUBLIC_NAME = "customerId";
  private static final String PUBLIC_VALUE = "C-42";

  private static final String REDACTION_MARKER = "\"[REDACTED]\"";

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
          .endEvent()
          .done();

  private static CamundaClient client;

  @BeforeAll
  static void deploy() {
    client.newDeployResourceCommand().addProcessModel(PROCESS, PROCESS_ID + ".bpmn").send().join();
  }

  @Test
  void shouldRedactDeclaredVariablesInSecondaryStorage() {
    // given
    final long processInstanceKey = createInstance();

    // when
    final var variables = awaitVariables(processInstanceKey, 3);

    // then
    assertThat(valueOf(variables, SENSITIVE_NAME)).isEqualTo(REDACTION_MARKER);
    assertThat(valueOf(variables, SENSITIVE_OBJECT_NAME)).isEqualTo(REDACTION_MARKER);
  }

  @Test
  void shouldNotRedactUndeclaredVariablesInSecondaryStorage() {
    // given
    final long processInstanceKey = createInstance();

    // when
    final var variables = awaitVariables(processInstanceKey, 3);

    // then
    assertThat(valueOf(variables, PUBLIC_NAME)).isEqualTo("\"" + PUBLIC_VALUE + "\"");
  }

  @Test
  void shouldKeepTheVariableVisibleInSecondaryStorage() {
    // given -- an operator must still see that the variable exists, only not its value
    final long processInstanceKey = createInstance();

    // when
    final var variables = awaitVariables(processInstanceKey, 3);

    // then
    assertThat(variables)
        .extracting(Variable::getName)
        .contains(SENSITIVE_NAME, SENSITIVE_OBJECT_NAME, PUBLIC_NAME);
  }

  @Test
  void shouldStillGiveTheJobWorkerThePlainValue() {
    // given -- job activation reads engine state, which redaction deliberately does not touch
    final long processInstanceKey = createInstance();

    // when
    final ActivatedJob job = activateJobFor(processInstanceKey);

    // then
    assertThat(job.getVariablesAsMap())
        .containsEntry(SENSITIVE_NAME, SENSITIVE_VALUE)
        .containsEntry(SENSITIVE_OBJECT_NAME, SENSITIVE_OBJECT_VALUE)
        .containsEntry(PUBLIC_NAME, PUBLIC_VALUE);
  }

  private static long createInstance() {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(
            Map.of(
                SENSITIVE_NAME, SENSITIVE_VALUE,
                SENSITIVE_OBJECT_NAME, SENSITIVE_OBJECT_VALUE,
                PUBLIC_NAME, PUBLIC_VALUE))
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static List<Variable> awaitVariables(
      final long processInstanceKey, final int expectedCount) {
    return Awaitility.await("variables of " + processInstanceKey + " reached secondary storage")
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(200))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items(),
            items -> items.size() >= expectedCount);
  }

  private static ActivatedJob activateJobFor(final long processInstanceKey) {
    return Awaitility.await("a job of " + processInstanceKey + " is activatable")
        .atMost(Duration.ofMinutes(1))
        .pollInterval(Duration.ofMillis(200))
        .ignoreExceptions()
        .until(
            () ->
                client
                    .newActivateJobsCommand()
                    .jobType(JOB_TYPE)
                    .maxJobsToActivate(10)
                    .send()
                    .join()
                    .getJobs()
                    .stream()
                    .filter(j -> j.getProcessInstanceKey() == processInstanceKey)
                    .findFirst()
                    .orElse(null),
            Objects::nonNull);
  }

  private static String valueOf(final List<Variable> variables, final String name) {
    return variables.stream()
        .filter(v -> name.equals(v.getName()))
        .map(Variable::getValue)
        .findFirst()
        .orElseThrow(() -> new AssertionError("no variable named " + name));
  }
}
