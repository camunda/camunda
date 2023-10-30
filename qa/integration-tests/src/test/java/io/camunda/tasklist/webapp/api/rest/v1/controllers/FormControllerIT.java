/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class FormControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void getEmbeddedForm() {
    // given
    final var formId = "userTask:Form_1";
    final var bpmnProcessId = "userTaskFormProcess";
    final var flowNodeBpmnId = "taskA";

    tester
        .having()
        .deployProcess("userTaskForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 1)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 1);

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
              assertThat(form.getSchema()).isNotBlank();
              assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
            });
  }

  @Test
  public void getLinkedFormHighestVersion() {
    // given
    final var formId = "Form_0mik7px";
    final var bpmnProcessId = "Process_11hxie4";
    final var flowNodeBpmnId = "taskA";

    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV1.form")
        .send()
        .join();
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV2.form")
        .send()
        .join();

    tester
        .having()
        .deployProcess("formIdProcessDeployed.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 1)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 1);

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
              assertThat(form.getSchema()).isNotBlank().contains("taglist");
              assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
            });
  }

  @Test
  public void getLinkedFormHighestVersionV1() {
    // given
    final var formId = "Form_0mik7px";
    final var bpmnProcessId = "Process_11hxie4";
    final var flowNodeBpmnId = "taskA";

    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV1.form")
        .send()
        .join();
    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV2.form")
        .send()
        .join();

    tester
        .having()
        .deployProcess("formIdProcessDeployed.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 1)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 1);

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
              assertThat(form.getSchema()).isNotBlank().doesNotContain("taglist");
              assertThat(form.getTenantId()).isEqualTo(DEFAULT_TENANT_IDENTIFIER);
            });
  }

  @Test
  public void getEmbeddedFormReturns404IfVersionIsPassed() {
    // given
    final var formId = "userTask:Form_1";
    final var bpmnProcessId = "userTaskFormProcess";
    final var flowNodeBpmnId = "taskA";

    tester
        .having()
        .deployProcess("userTaskForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstances(bpmnProcessId, 1)
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, 1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                .param("processDefinitionKey", tester.getProcessDefinitionKey())
                .param("version", String.valueOf(1L)));

    // then
    assertThat(result).hasHttpStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void getFormWhenFormIsNotFoundThen404ErrorExpected() {
    // given
    final var formId = "unknown:Form_404";
    final var processDefinitionKey = "test";

    // when
    final var errorResult =
        mockMvcHelper.doRequest(
            get(TasklistURIs.FORMS_URL_V1.concat("/{formId}"), formId)
                .param("processDefinitionKey", processDefinitionKey));

    // then
    assertThat(errorResult)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .hasApplicationProblemJsonContentType()
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasInstanceId()
        .hasMessage("form with id %s was not found", formId);
  }
}
