/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.VariableResponse;
import io.camunda.tasklist.webapp.graphql.entity.VariableDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.VariableService;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class VariablesControllerTest {

  @Mock private VariableService variableService;

  @InjectMocks private VariablesController instance;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void getVariableById() throws Exception {
    // Given
    final var variableId = "var-2222";
    final var providedVariable =
        new VariableDTO().setId(variableId).setName("a").setValue("24.12").setPreviewValue("24.12");
    final var expectedVariable =
        new VariableResponse().setId(variableId).setName("a").setValue("24.12");
    when(variableService.getVariable(variableId, Collections.emptySet()))
        .thenReturn(providedVariable);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get(
                        TasklistURIs.VARIABLES_URL_V1.concat("/{variableId}"), variableId)
                    .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(responseAsString, VariableResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedVariable);
  }
}
