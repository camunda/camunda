/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auth;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.ResourceType.RESOURCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.search.response.Form;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class FormAuthorizationIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withBasicAuth().withAuthorizationsEnabled();

  private static final String AUTHORIZED_FORM_ID = "test";
  private static final String AUTHORIZED = "authorizedUser";
  private static final String RESTRICTED = "restrictedUser";

  private static final AtomicLong AUTHORIZED_FORM_KEY = new AtomicLong();
  private static final AtomicLong UNAUTHORIZED_FORM_KEY = new AtomicLong();

  @UserDefinition
  private static final TestUser AUTHORIZED_USER =
      new TestUser(
          AUTHORIZED,
          "password",
          List.of(
              new Permissions(RESOURCE, CREATE, List.of("*")),
              new Permissions(RESOURCE, READ, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(
          RESTRICTED,
          "password",
          List.of(new Permissions(RESOURCE, READ, List.of(AUTHORIZED_FORM_ID))));

  @BeforeAll
  static void setUp(@Authenticated final CamundaClient adminClient) {
    AUTHORIZED_FORM_KEY.set(deployForm(adminClient, "form/form.form"));
    UNAUTHORIZED_FORM_KEY.set(deployForm(adminClient, "form/job_search_process.form"));
    TestHelper.waitForFormToBeIndexed(adminClient, AUTHORIZED_FORM_KEY.get());
    TestHelper.waitForFormToBeIndexed(adminClient, UNAUTHORIZED_FORM_KEY.get());
  }

  @Test
  void shouldGetFormByKeyWhenAuthorized(@Authenticated(AUTHORIZED) final CamundaClient userClient) {
    // when
    final Form form = userClient.newFormGetRequest(AUTHORIZED_FORM_KEY.get()).send().join();

    // then
    assertThat(form).isNotNull();
    assertThat(form.getFormId()).isEqualTo(AUTHORIZED_FORM_ID);
  }

  @Test
  void shouldGetFormByKeyWhenRestrictedToSpecificFormId(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final Form form = userClient.newFormGetRequest(AUTHORIZED_FORM_KEY.get()).send().join();

    // then
    assertThat(form).isNotNull();
    assertThat(form.getFormId()).isEqualTo(AUTHORIZED_FORM_ID);
  }

  @Test
  void shouldReturnForbiddenForGetFormByKeyWhenUnauthorized(
      @Authenticated(RESTRICTED) final CamundaClient userClient) {
    // when
    final ThrowingCallable executeGet =
        () -> userClient.newFormGetRequest(UNAUTHORIZED_FORM_KEY.get()).send().join();

    // then
    final var problemException =
        assertThatExceptionOfType(ProblemException.class).isThrownBy(executeGet).actual();
    assertThat(problemException.code()).isEqualTo(403);
    assertThat(problemException.details().getDetail())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'RESOURCE'");
  }

  private static long deployForm(final CamundaClient client, final String resourcePath) {
    final DeploymentEvent deployment =
        client.newDeployResourceCommand().addResourceFromClasspath(resourcePath).send().join();
    return deployment.getForm().getFirst().getFormKey();
  }
}
