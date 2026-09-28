/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilFailedJobIncident;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Not annotated with {@code @CompatibilityTest}: these cases assert #64014's fixed behavior, which
 * older, already-released server versions don't have. The nightly cross-version compatibility suite
 * reuses current test source against those old servers, so a compatibility test here would fail
 * there once backported, for a version mismatch rather than a real bug.
 */
@MultiDbTest
class IncidentErrorMessageFilterIT {

  private static final String JOB_TYPE = "taskAExecutionListener";
  private static final String ERROR_MESSAGE = "fail job";

  private static CamundaClient camundaClient;

  private static long jobKey;

  @BeforeAll
  static void beforeAll() {
    deployResource(camundaClient, "process/job_search_process.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    startProcessInstance(camundaClient, "job_search_test_process");
    waitForProcessInstancesToStart(camundaClient, 1);

    jobKey =
        camundaClient
            .newJobSearchRequest()
            .filter(f -> f.type(JOB_TYPE))
            .send()
            .join()
            .singleItem()
            .getJobKey();
    camundaClient.newFailCommand(jobKey).retries(0).errorMessage(ERROR_MESSAGE).send().join();
    waitUntilFailedJobIncident(camundaClient, 1);
  }

  @Test
  void shouldFilterByErrorMessageEqualsMultiWordValue() {
    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.errorMessage(f2 -> f2.eq(ERROR_MESSAGE)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(jobKey);
  }

  @Test
  void shouldFilterByErrorMessageEqualsCaseInsensitively() {
    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.errorMessage(f2 -> f2.eq(ERROR_MESSAGE.toUpperCase())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(jobKey);
  }

  @Test
  void shouldFilterByErrorMessageLikeSingleWordValueCaseInsensitively() {
    // errorMessage is an analyzed text field, so $like matches against individual indexed
    // tokens - a pattern spanning multiple words (with a literal space) can never match, since
    // no single token contains a space. This is pre-existing, unrelated to #64014.
    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.errorMessage(f2 -> f2.like("*JOB*")))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(jobKey);
  }
}
