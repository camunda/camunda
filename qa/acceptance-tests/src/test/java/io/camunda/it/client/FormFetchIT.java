/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.Form;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class FormFetchIT {

  private static CamundaClient camundaClient;
  private static long deployedFormKey;
  private static String deployedFormId;

  @BeforeAll
  static void setUp() {
    // Deploy a form
    final DeploymentEvent deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form.form")
            .send()
            .join();

    assertThat(deployment.getForm()).hasSize(1);
    deployedFormKey = deployment.getForm().getFirst().getFormKey();
    deployedFormId = deployment.getForm().getFirst().getFormId();

    // Wait for form to be indexed
    TestHelper.waitForFormToBeIndexed(camundaClient, deployedFormKey);
  }

  @Test
  void shouldGetFormByKey() {
    // when
    final Form form = camundaClient.newFormGetRequest(deployedFormKey).send().join();

    // then
    assertThat(form).isNotNull();
    assertThat(form.getFormKey()).isEqualTo(String.valueOf(deployedFormKey));
    assertThat(form.getFormId()).isEqualTo(deployedFormId);
    assertThat(form.getVersion()).isEqualTo(1);
    assertThat(form.getSchema()).isNotNull();
    assertThat(form.getSchema()).contains("\"type\": \"default\"");
    assertThat(form.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldRejectGetIfFormDoesNotExist() {
    // given
    final long nonExistentFormKey = 999999999L;

    // when / then
    assertThatThrownBy(() -> camundaClient.newFormGetRequest(nonExistentFormKey).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldGetFormWithDifferentVersions() {
    // given - deploy a second version of the same form
    final DeploymentEvent secondDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form_v2.form")
            .send()
            .join();

    assertThat(secondDeployment.getForm()).hasSize(1);
    final long secondFormKey = secondDeployment.getForm().getFirst().getFormKey();
    final String secondFormId = secondDeployment.getForm().getFirst().getFormId();

    // Wait for form to be indexed
    TestHelper.waitForFormToBeIndexed(camundaClient, secondFormKey);

    // when - get the second version
    final Form formV2 = camundaClient.newFormGetRequest(secondFormKey).send().join();

    // then
    assertThat(formV2).isNotNull();
    assertThat(formV2.getFormKey()).isEqualTo(String.valueOf(secondFormKey));
    assertThat(formV2.getFormId()).isEqualTo(secondFormId);
    assertThat(formV2.getVersion()).isEqualTo(1);
    assertThat(formV2.getTenantId()).isEqualTo("<default>");

    // when - get the first version again
    final Form formV1 = camundaClient.newFormGetRequest(deployedFormKey).send().join();

    // then - should still be accessible
    assertThat(formV1).isNotNull();
    assertThat(formV1.getFormKey()).isEqualTo(String.valueOf(deployedFormKey));
    assertThat(formV1.getFormId()).isEqualTo(deployedFormId);
  }
}
