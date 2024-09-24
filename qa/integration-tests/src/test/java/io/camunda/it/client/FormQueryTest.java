/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class FormQueryTest {
  private static Long formKey;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static ZeebeClient camundaClient;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
  }

  @BeforeAll
  static void beforeAll() {
    camundaClient = testStandaloneCamunda.newClientBuilder().build();
    deployForm("form/form_test.form");

    waitForFormBeingExported();
  }

  @Test
  public void shouldRetrieveFormByKey() {
    final var result = camundaClient.newFormGetRequest(formKey).send().join();

    assertThat(result.getKey()).isEqualTo(formKey);
  }

  private static void deployForm(final String resource) {
    final var formDeployed =
        camundaClient.newDeployResourceCommand().addResourceFromClasspath(resource).send().join();

    formKey = formDeployed.getForm().getLast().getFormKey();
  }

  private static void waitForFormBeingExported() {
    Awaitility.await("should receive data from ES")
        .atMost(Duration.ofMinutes(1))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = camundaClient.newFormGetRequest(formKey).send().join();
              assertThat(result).isNotNull();
            });
  }
}
