/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TasklistZeebeIntegrationTest.DEFAULT_USER_ID;
import static org.mockito.Mockito.mock;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.webapp.service.OrganizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(DEFAULT_USER_ID)
public abstract class TasklistZeebeIntegrationTest extends SessionlessTasklistZeebeIntegrationTest {
  public static final String DEFAULT_USER_ID = "demo";

  @MockBean protected CamundaAuthenticationProvider authenticationProvider;

  private MockedStatic<TasklistAuthenticationUtil> authenticationUtil;

  @Override
  @BeforeEach
  public void before() {
    super.before();
    authenticationUtil = Mockito.mockStatic(TasklistAuthenticationUtil.class);
    setDefaultCurrentUser();
  }

  @Override
  @AfterEach
  public void after() {
    setDefaultCurrentUser();
    authenticationUtil.close();
    super.after();
  }

  protected void setDefaultCurrentUser() {
    setCurrentUser(getDefaultCurrentUser());
  }

  protected CamundaAuthentication getDefaultCurrentUser() {
    return CamundaAuthentication.of(b -> b.user(DEFAULT_USER_ID));
  }

  protected void setCurrentUser(final CamundaAuthentication user) {
    setCurrentUser(user, false);
  }

  protected void setCurrentUser(final CamundaAuthentication user, final boolean isApiUser) {
    final String organisation =
        user.authenticatedUsername().equals(DEFAULT_USER_ID)
            ? OrganizationService.DEFAULT_ORGANIZATION
            : user.authenticatedUsername() + "-org";
    Mockito.when(organizationService.getOrganizationIfPresent()).thenReturn(organisation);

    final var authentication = mock(CamundaAuthentication.class);
    Mockito.when(authenticationProvider.getCamundaAuthentication()).thenReturn(authentication);
    Mockito.when(authentication.authenticatedUsername()).thenReturn(user.authenticatedUsername());
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(isApiUser);
  }
}
