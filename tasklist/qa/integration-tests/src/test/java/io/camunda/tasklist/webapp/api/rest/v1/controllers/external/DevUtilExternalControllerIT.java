/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class DevUtilExternalControllerIT extends TasklistZeebeIntegrationTest {
  private MockMvcHelper mockMvcHelper;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private WebApplicationContext context;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void shouldReturn404() {
    final var result =
        mockMvcHelper.doRequest(post(TasklistURIs.DEV_UTIL_URL_V1.concat("/recreateData")));
    assertThat(result).hasHttpStatus(HttpStatus.NOT_FOUND);
  }
}
