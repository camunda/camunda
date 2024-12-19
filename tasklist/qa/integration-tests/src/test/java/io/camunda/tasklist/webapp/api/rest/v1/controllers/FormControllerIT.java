/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static io.camunda.zeebe.protocol.record.value.TenantOwned.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
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
            .startProcessInstance(bpmnProcessId)
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

        camundaClient
            .newDeployResourceCommand()
            .addResourceFromClasspath("formDeployedV1.form")
            .send()
            .join();

        v2DeployedData =
            camundaClient
                .newDeployResourceCommand()
                .addResourceFromClasspath("formDeployedV2.form")
                .send()
                .join();

        lastVersionDeployedData =
            camundaClient
                .newDeployResourceCommand()
                .addResourceFromClasspath("formDeployedV3.form")
                .send()
                .join();

        final var formKey =
            lastVersionDeployedData.getForm().stream().findFirst().get().getFormKey();
        camundaClient.newDeleteResourceCommand(formKey).send().join();

        tester
            .having()
            .deployProcess("formIdProcessDeployed.bpmn")
            .waitUntil()
            .processIsDeployed()
            .and()
            .startProcessInstance(bpmnProcessId)
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
