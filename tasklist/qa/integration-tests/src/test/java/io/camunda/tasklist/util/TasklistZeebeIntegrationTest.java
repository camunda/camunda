/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TasklistZeebeIntegrationTest.DEFAULT_USER_ID;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.tasklist.webapp.service.OrganizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WithMockUser(DEFAULT_USER_ID)
public abstract class TasklistZeebeIntegrationTest extends SessionlessTasklistZeebeIntegrationTest {
  public static final String DEFAULT_USER_ID = "demo";

  @MockitoBean protected CamundaAuthenticationProvider authenticationProvider;

  @Override
  @BeforeEach
  public void before() {
    super.before();
    setDefaultCurrentUser();
  }

  @Override
  @AfterEach
  public void after() {
    setDefaultCurrentUser();
    super.after();
  }

  protected void setDefaultCurrentUser() {
    setCurrentUser(getDefaultCurrentUser());
  }

  protected CamundaAuthentication getDefaultCurrentUser() {
    return CamundaAuthentication.of(b -> b.user(DEFAULT_USER_ID));
  }

  protected void setCurrentUser(final CamundaAuthentication user) {
    final String principalName =
        user.authenticatedUsername() != null
            ? user.authenticatedUsername()
            : user.authenticatedClientId();

    final String organisation =
        principalName.equals(DEFAULT_USER_ID)
            ? OrganizationService.DEFAULT_ORGANIZATION
            : user.authenticatedUsername() + "-org";
    Mockito.when(organizationService.getOrganizationIfPresent()).thenReturn(organisation);

    Mockito.when(authenticationProvider.getCamundaAuthentication()).thenReturn(user);
  }
}
