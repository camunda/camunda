/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.zeebe.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
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
              .setBpmnId(formId)
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
              .setBpmnId(formId)
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
              .setBpmnId(formId)
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
