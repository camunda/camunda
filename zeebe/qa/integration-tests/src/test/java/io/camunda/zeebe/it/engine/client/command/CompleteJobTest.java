/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CompleteJobCommandStep1.CompleteJobCommandJobResultStep;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@ZeebeIntegration
public final class CompleteJobTest {

  @AutoClose CamundaClient client;

  @TestZeebe
  final TestStandaloneBroker zeebe =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  public void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteJobWithoutVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCommand(client, useRest, jobKey).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteJobNullVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCommand(client, useRest, jobKey).variables("null").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteJobWithVariables(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    getCommand(client, useRest, jobKey).variables("{\"foo\":\"bar\"}").send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).containsOnly(entry("foo", "bar")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfVariablesAreInvalid(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    if (useRest) {
      assertThatThrownBy(() -> getCommand(client, useRest, jobKey).variables("[]").send().join())
          .hasMessageContaining("Failed to deserialize json '[]' to 'Map<String, Object>'");
    } else {
      assertThatThrownBy(() -> getCommand(client, useRest, jobKey).variables("[]").send().join())
          .hasMessageContaining(
              "Property 'variables' is invalid: Expected document to be a root level object, but was 'ARRAY'");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfVariableIsBigIntTooLong(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    final var expectedMessage = "MessagePack cannot serialize BigInteger larger than 2^64-1";
    final var variables = "{\"mybigintistoolong\":123456789012345678901234567890}";

    // when
    assertThatThrownBy(() -> getCommand(client, useRest, jobKey).variables(variables).send().join())
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldRejectIfJobIsAlreadyCompleted(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // given
    getCommand(client, useRest, jobKey).send().join();

    // when
    final var expectedMessage =
        String.format("Expected to complete job with key '%d', but no such job was found", jobKey);
    assertThatThrownBy(() -> getCommand(client, useRest, jobKey).send().join())
        .hasMessageContaining(expectedMessage);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteJobWithSingleVariable(final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);
    final String key = "key";
    final var value = "value";
    // when
    getCommand(client, useRest, jobKey).variable(key, value).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getVariables()).containsOnly(entry(key, value)));
  }

  @ParameterizedTest
  @CsvSource({"true, true", "true, false", "false, true", "false, false"})
  public void shouldCompleteJobWhenResultDeniedIsSet(
      final boolean useRest, final boolean denied, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);
    // when
    getCommand(client, useRest, jobKey).withResult(r -> r.forUserTask().deny(denied)).send().join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getResult().isDenied()).isEqualTo(denied));
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldCompleteJobWithResultDeniedNotSet(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);
    // when
    getCommand(client, useRest, jobKey)
        .withResult(CompleteJobCommandJobResultStep::forUserTask)
        .send()
        .join();

    // then
    ZeebeAssertHelper.assertJobCompleted(
        jobType, (job) -> assertThat(job.getResult().isDenied()).isFalse());
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldThrowErrorWhenTryToCompleteJobWithNullVariable(
      final boolean useRest, final TestInfo testInfo) {
    // given
    final String jobType = "job-" + testInfo.getDisplayName();
    final var jobKey = resourcesHelper.createSingleJob(jobType);

    // when
    assertThatThrownBy(() -> getCommand(client, useRest, jobKey).variable(null, null).send().join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  private CompleteJobCommandStep1 getCommand(
      final CamundaClient client, final boolean useRest, final long jobKey) {
    final CompleteJobCommandStep1 completeJobCommandStep1 = client.newCompleteCommand(jobKey);
    return useRest ? completeJobCommandStep1.useRest() : completeJobCommandStep1.useGrpc();
  }
}
