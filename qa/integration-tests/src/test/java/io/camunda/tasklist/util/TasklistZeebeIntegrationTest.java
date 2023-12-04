/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util;

import static io.camunda.tasklist.util.TasklistZeebeIntegrationTest.DEFAULT_USER_ID;
import static org.mockito.ArgumentMatchers.any;

import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;

@WithMockUser(DEFAULT_USER_ID)
public abstract class TasklistZeebeIntegrationTest extends SessionlessTasklistZeebeIntegrationTest {
  public static final String DEFAULT_USER_ID = "demo";

  public static final String DEFAULT_DISPLAY_NAME = "Demo User";

  @MockBean protected UserReader userReader;

  @Before
  public void before() {
    super.before();
    setDefaultCurrentUser();
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

  protected void setCurrentUser(UserDTO user) {
    Mockito.when(userReader.getCurrentUserId()).thenReturn(user.getUserId());
    Mockito.when(userReader.getCurrentUser()).thenReturn(user);
    Mockito.when(userReader.getUsersByUsernames(any())).thenReturn(List.of(user));
    final String organisation =
        user.getUserId().equals(DEFAULT_USER_ID)
            ? UserReader.DEFAULT_ORGANIZATION
            : user.getUserId() + "-org";
    Mockito.when(userReader.getCurrentOrganizationId()).thenReturn(organisation);
  }

  @After
  public void after() {
    setDefaultCurrentUser();
    super.after();
  }
}
