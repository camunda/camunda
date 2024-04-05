/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.UserReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserInternalControllerTest {

  private MockMvc mockMvc;

  @Mock private UserReader userReader;

  @InjectMocks private UserInternalController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void getCurrentUser() throws Exception {
    // give
    final var user =
        new UserDTO()
            .setUserId("ivan4000")
            .setDisplayName("ivan4000")
            .setPermissions(List.of(Permission.READ));
    when(userReader.getCurrentUser()).thenReturn(user);

    // when
    final var responseAsString =
        mockMvc
            .perform(get(TasklistURIs.USERS_URL_V1.concat("/current")))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, UserDTO.class);

    // then
    assertThat(result).isEqualTo(user);
  }
}
