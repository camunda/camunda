/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.es.FormReader;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FormControllerTest {

  private MockMvc mockMvc;

  @Mock private FormReader formReader;

  @InjectMocks private FormController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void getForm() throws Exception {
    // Given
    final var formId = "userTaskForm_111";
    final var processDefinitionKey = "100001";
    final var formDTO =
        new FormDTO().setId(formId).setProcessDefinitionId(processDefinitionKey).setSchema("{}");
    final var expectedFormResponse =
        new FormResponse()
            .setId(formId)
            .setProcessDefinitionKey(processDefinitionKey)
            .setSchema("{}");
    when(formReader.getFormDTO(formId, processDefinitionKey)).thenReturn(formDTO);

    // When
    final var responseAsString =
        mockMvc
            .perform(
                get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                    .param("processDefinitionKey", processDefinitionKey))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, FormResponse.class);

    // Then
    assertThat(result).isEqualTo(expectedFormResponse);
  }

  @Test
  void getFormWhenRequiredProcessDefinitionKeyNotProvided() throws Exception {
    // Given
    final var formId = "userTaskForm_222";

    // When
    final var errorResponseAsString =
        mockMvc
            .perform(get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var errorResult = CommonUtils.OBJECT_MAPPER.readValue(errorResponseAsString, Error.class);

    // Then
    verifyNoInteractions(formReader);
    assertThat(errorResult)
        .satisfies(
            err -> {
              assertThat(err.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
              assertThat(err.getInstance()).isNotBlank();
              assertThat(err.getMessage())
                  .isEqualTo(
                      "Required request parameter 'processDefinitionKey' for method parameter type String is not present");
            });
  }
}
