/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TasklistZeebeIntegrationTest.DEFAULT_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.camunda.authentication.entity.CamundaUserDTO;
import io.camunda.authentication.service.CamundaUserService;
import io.camunda.tasklist.webapp.dto.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistAuthenticationUtil;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(DEFAULT_USER_ID)
public abstract class TasklistZeebeIntegrationTest extends SessionlessTasklistZeebeIntegrationTest {
  public static final String DEFAULT_USER_ID = "demo";

  public static final String DEFAULT_DISPLAY_NAME = "Demo User";

  @MockBean protected UserReader userReader;

  @Autowired protected CamundaUserService camundaUserService;

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

  protected UserDTO getDefaultCurrentUser() {
    return new UserDTO()
        .setUserId(DEFAULT_USER_ID)
        .setDisplayName(DEFAULT_DISPLAY_NAME)
        .setPermissions(List.of(Permission.WRITE));
  }

  protected void setCurrentUser(final UserDTO user) {
    setCurrentUser(user, false);
  }

  protected void setCurrentUser(final UserDTO user, final boolean isApiUser) {
    Mockito.when(userReader.getCurrentUserId()).thenReturn(user.getUserId());
    Mockito.when(userReader.getCurrentUser()).thenReturn(user);
    Mockito.when(userReader.getUsersByUsernames(any())).thenReturn(List.of(user));
    final String organisation =
        user.getUserId().equals(DEFAULT_USER_ID)
            ? UserReader.DEFAULT_ORGANIZATION
            : user.getUserId() + "-org";
    Mockito.when(userReader.getCurrentOrganizationId()).thenReturn(organisation);
    authenticationUtil.when(TasklistAuthenticationUtil::isApiUser).thenReturn(isApiUser);

    Mockito.when(camundaUserService.getCurrentUser()).thenReturn(mock(CamundaUserDTO.class));
    Mockito.when(camundaUserService.getCurrentUser().userId()).thenReturn(user.getUserId());
  }
}
