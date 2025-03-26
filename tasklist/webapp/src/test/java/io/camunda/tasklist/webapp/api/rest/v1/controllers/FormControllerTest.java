/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.webapps.schema.entities.form.FormEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

  @Mock private FormStore formStore;

  @InjectMocks private FormController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Nested
  class EmbeddedFormTests {
    @Test
    void getEmbeededForm() throws Exception {
      // Given
      final var formId = "userTaskForm_111";
      final var processDefinitionKey = "100001";
      final var formEntity =
          new FormEntity()
              .setId(processDefinitionKey.concat("_").concat(formId))
              .setFormId(formId)
              .setProcessDefinitionId(processDefinitionKey)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      final var expectedFormResponse =
          new FormResponse()
              .setId(formId)
              .setProcessDefinitionKey(processDefinitionKey)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      when(formStore.getForm(formId, processDefinitionKey, null)).thenReturn(formEntity);

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
    void getEmbeededFormReturnsNotFoundWhenVersionIsPassed() throws Exception {
      // Given
      final var formId = "userTaskForm_111";
      final var processDefinitionKey = "100001";

      when(formStore.getForm(formId, processDefinitionKey, null))
          .thenThrow(NotFoundApiException.class);

      // Then
      mockMvc
          .perform(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                  .param("processDefinitionKey", processDefinitionKey)
                  .param("version", ""))
          .andDo(print())
          .andExpect(status().isNotFound())
          .andReturn();
    }
  }

  @Nested
  class LinkedFormTests {
    @Test
    void getLinkedFormWhenVersionIsPassed() throws Exception {
      // Given
      final var formId = "form";
      final var formKey = "232323323";
      final var processDefinitionKey = "1234";
      final long version = 1;
      final var formEntity =
          new FormEntity()
              .setId(formKey)
              .setFormId(formId)
              .setProcessDefinitionId(null)
              .setEmbedded(false)
              .setVersion(version)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      final var expectedFormResponse =
          new FormResponse()
              .setId(formId)
              .setProcessDefinitionKey(processDefinitionKey)
              .setVersion(version)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      when(formStore.getForm(formId, processDefinitionKey, version)).thenReturn(formEntity);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                      .param("processDefinitionKey", processDefinitionKey)
                      .param("version", String.valueOf(version)))
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
    void getLinkedFormWhenVersionIsNotPassed() throws Exception {
      // Given
      final var formId = "form";
      final var formKey = "232323323";
      final var processDefinitionKey = "1234";
      final long version = 2;
      final var formEntity =
          new FormEntity()
              .setId(formKey)
              .setFormId(formId)
              .setProcessDefinitionId(null)
              .setEmbedded(false)
              .setVersion(version)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      final var expectedFormResponse =
          new FormResponse()
              .setId(formId)
              .setProcessDefinitionKey(processDefinitionKey)
              .setVersion(version)
              .setSchema("{}")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      when(formStore.getForm(formId, processDefinitionKey, null)).thenReturn(formEntity);

      // When
      final var responseAsString =
          mockMvc
              .perform(
                  get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                      .param("processDefinitionKey", processDefinitionKey)
                      .param("version", ""))
              .andDo(print())
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, FormResponse.class);

      // Then
      assertThat(result).isEqualTo(expectedFormResponse);
    }
  }

  @Nested
  class ExceptionPathTest {
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
      final var errorResult =
          CommonUtils.OBJECT_MAPPER.readValue(errorResponseAsString, Error.class);

      // Then
      verifyNoInteractions(formStore);
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
}
