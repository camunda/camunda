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

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class FormControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;
  private MockMvcHelper mockMvcHelper;

  private boolean initializedLinkedTests = false;
  private boolean initializedEmbeddedTests = false;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Nested
  class EmbeddedFormTests {
    final String bpmnProcessId = "userTaskFormProcess";
    final String flowNodeBpmnId = "taskA";

    @BeforeEach
    public void setUp() {
      if (!initializedEmbeddedTests) {
        tester
            .having()
            .deployProcess("userTaskForm.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 1)
            .waitUntil()
            .tasksAreCreated(flowNodeBpmnId, 1);
        initializedEmbeddedTests = true;
      }
    }

    @Test
    public void getEmbeddedForm() {
      // given
      final var formId = "userTask:Form_1";

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                  .param("processDefinitionKey", tester.getProcessDefinitionKey()));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, FormResponse.class)
          .satisfies(
              form -> {
                assertThat(form.getId()).isEqualTo(formId);
                assertThat(form.getIsDeleted()).isEqualTo(false);
                assertThat(form.getProcessDefinitionKey())
                    .isEqualTo(tester.getProcessDefinitionKey());
                assertThat(form.getSchema()).isNotBlank();
                assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
              });
    }
  }

  @Nested
  class LinkedFormTests {
    private DeploymentEvent lastVersionDeployedData = null;
    private DeploymentEvent v2DeployedData = null;

    @BeforeEach
    public void setUp() {
      if (!initializedLinkedTests) {
        final var bpmnProcessId = "Process_11hxie4";
        final var flowNodeBpmnId = "Activity_14emqkd";

        zeebeClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("formDeployedV1.form")
            .send()
            .join();

        v2DeployedData =
            zeebeClient
                .newDeployResourceCommand()
                .addResourceFromClasspath("formDeployedV2.form")
                .send()
                .join();

        lastVersionDeployedData =
            zeebeClient
                .newDeployResourceCommand()
                .addResourceFromClasspath("formDeployedV3.form")
                .send()
                .join();

        final var formKey =
            lastVersionDeployedData.getForm().stream().findFirst().get().getFormKey();
        zeebeClient.newDeleteResourceCommand(formKey).send().join();

        tester
            .having()
            .deployProcess("formIdProcessDeployed.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstances(bpmnProcessId, 1)
            .waitUntil()
            .taskIsCreated(flowNodeBpmnId);

        initializedLinkedTests = true;
      }
    }

    @Test
    public void getLinkedFormDeleted() {
      // given
      final var formId = "Form_0mik7px";

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                  .param("processDefinitionKey", tester.getProcessDefinitionKey())
                  .param("version", "3"));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, FormResponse.class)
          .satisfies(
              form -> {
                assertThat(form.getId()).isEqualTo(formId);
                assertThat(form.getProcessDefinitionKey())
                    .isEqualTo(tester.getProcessDefinitionKey());
                assertThat(form.getIsDeleted()).isEqualTo(true);
                assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
              });
    }

    @Test
    public void getLinkedFormVersionV1() {
      // given
      final var formId = "Form_0mik7px";

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                  .param("processDefinitionKey", tester.getProcessDefinitionKey())
                  .param("version", String.valueOf(1L)));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, FormResponse.class)
          .satisfies(
              form -> {
                assertThat(form.getId()).isEqualTo(formId);
                assertThat(form.getProcessDefinitionKey())
                    .isEqualTo(tester.getProcessDefinitionKey());
                assertThat(form.getIsDeleted()).isEqualTo(false);
                assertThat(form.getSchema()).isNotBlank().doesNotContain("taglist");
                assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
              });
    }

    @Test
    public void getLinkedFormByFormKey() {
      // given
      final var formId = "Form_0mik7px";
      final var formKey = v2DeployedData.getForm().stream().findFirst().get().getFormKey();

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formKey)
                  .param("processDefinitionKey", tester.getProcessDefinitionKey()));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, FormResponse.class)
          .satisfies(
              form -> {
                assertThat(form.getId()).isEqualTo(formId);
                assertThat(form.getVersion()).isEqualTo(2L);
                assertThat(form.getProcessDefinitionKey())
                    .isEqualTo(tester.getProcessDefinitionKey());
                assertThat(form.getIsDeleted()).isEqualTo(false);
                assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
              });
    }

    @Test
    public void getLinkedHighestVersion() {
      // given
      final var formId = "Form_0mik7px";

      // when
      final var result =
          mockMvcHelper.doRequest(
              get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                  .param("processDefinitionKey", tester.getProcessDefinitionKey()));

      // then
      assertThat(result)
          .hasOkHttpStatus()
          .hasApplicationJsonContentType()
          .extractingContent(objectMapper, FormResponse.class)
          .satisfies(
              form -> {
                assertThat(form.getId()).isEqualTo(formId);
                assertThat(form.getProcessDefinitionKey())
                    .isEqualTo(tester.getProcessDefinitionKey());
                assertThat(form.getIsDeleted()).isEqualTo(false);
                assertThat(form.getVersion()).isEqualTo(2L);
                assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
              });
    }
  }
}
