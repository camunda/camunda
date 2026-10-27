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
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class GetFormIT {

  private static CamundaClient camundaClient;

  @Test
  void shouldGetFormByKey() {
    // given
    final DeploymentEvent deployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form.form")
            .send()
            .join();

    assertThat(deployment.getForm()).hasSize(1);
    final long formKey = deployment.getForm().getFirst().getFormKey();
    final String formId = deployment.getForm().getFirst().getFormId();
    TestHelper.waitForFormToBeIndexed(camundaClient, formKey);

    // when
    final Form form = camundaClient.newFormGetRequest(formKey).send().join();

    // then
    assertThat(form).isNotNull();
    assertThat(form.getFormKey()).isEqualTo(formKey);
    assertThat(form.getFormId()).isEqualTo(formId);
    assertThat(form.getVersion()).isGreaterThanOrEqualTo(1);
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
    // given - deploy two versions of the same form
    final DeploymentEvent firstDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form.form")
            .send()
            .join();

    assertThat(firstDeployment.getForm()).hasSize(1);
    final long firstFormKey = firstDeployment.getForm().getFirst().getFormKey();
    final String firstFormId = firstDeployment.getForm().getFirst().getFormId();
    TestHelper.waitForFormToBeIndexed(camundaClient, firstFormKey);

    final DeploymentEvent secondDeployment =
        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("form/form_v2.form")
            .send()
            .join();

    assertThat(secondDeployment.getForm()).hasSize(1);
    final long secondFormKey = secondDeployment.getForm().getFirst().getFormKey();
    final String secondFormId = secondDeployment.getForm().getFirst().getFormId();
    TestHelper.waitForFormToBeIndexed(camundaClient, secondFormKey);

    // when
    final Form formV1 = camundaClient.newFormGetRequest(firstFormKey).send().join();
    final Form formV2 = camundaClient.newFormGetRequest(secondFormKey).send().join();

    // then - both versions are accessible and v2 has a higher version number
    assertThat(formV1.getFormKey()).isEqualTo(firstFormKey);
    assertThat(formV1.getFormId()).isEqualTo(firstFormId);

    assertThat(formV2.getFormKey()).isEqualTo(secondFormKey);
    assertThat(formV2.getFormId()).isEqualTo(secondFormId);
    assertThat(formV2.getVersion()).isEqualTo(formV1.getVersion() + 1);
    assertThat(formV2.getTenantId()).isEqualTo("<default>");
  }
}
