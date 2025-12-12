/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static io.camunda.tasklist.util.TestCheck.PROCESS_IS_DEPLOYED_CHECK;
import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.TestCheck;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessExternalControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier(PROCESS_IS_DEPLOYED_CHECK)
  private TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private IdentityAuthorizationService identityAuthorizationService;

  private MockMvcHelper mockMvcHelper;

  @BeforeEach
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void getEmbeddedFormByProcessId() {
    // given
    final String bpmnProcessId = "startedByForm";
    final String formId = "testForm";

    tester
        .having()
        .deployProcess("startedByFormProcess.bpmn")
        .waitUntil()
        .processIsDeployed()
        .waitUntil()
        .formExists(formId);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, FormResponse.class)
        .satisfies(
            form -> {
              Assertions.assertThat(form.getId()).isEqualTo(formId);
            });
  }

  @Test
  public void getLinkedFormByProcessIdV1() {
    // given
    final String bpmnProcessId = "startedByFormLinked";
    final String formId = "Form_0mik7px";

    zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath("formDeployedV1.form")
        .send()
        .join();

    tester
        .having()
        .deployProcess("startedByLinkedForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .waitUntil()
        .formExists(formId);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, FormResponse.class)
        .satisfies(
            form -> {
              Assertions.assertThat(form.getId()).isEqualTo(formId);
            });
  }

  @Test
  public void getLinkedFormByProcessIdWithUpdatedForm() {
    // given
    final String bpmnProcessId = "startedByFormLinked";

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
        .deployProcess("startedByLinkedForm.bpmn")
        .waitUntil()
        .processIsDeployed()
        .waitUntil()
        .formExists("Form_0mik7px", 2L);

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId));

    // then
    assertThat(result)
        .hasOkHttpStatus()
        .hasApplicationJsonContentType()
        .extractingContent(objectMapper, FormResponse.class)
        .satisfies(
            form -> {
              Assertions.assertThat(form.getSchema()).contains("taglist"); // Taglist is only in V2
            });
  }

  @Test
  public void shouldReturn404ToProcessThatCannotBeStarted() {
    final String bpmnProcessId = "Process_1g4wt4m";

    tester.having().deployProcess("simple_process.bpmn").waitUntil().processIsDeployed();

    // when
    final var result =
        mockMvcHelper.doRequest(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId));

    // then
    assertThat(result).hasHttpStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void startProcess() throws Exception {
    final var result =
        startProcessDeployInvokeAndReturn("startedByFormProcess.bpmn", "startedByForm");
    assertThat(result)
        .hasHttpStatus(HttpStatus.OK)
        .extractingContent(objectMapper, ProcessInstanceDTO.class)
        .satisfies(
            processInstanceDTO -> {
              Assertions.assertThat(processInstanceDTO.getId()).isNotNull();
            });

    verifyNoInteractions(identityAuthorizationService);
  }

  @Test
  public void shouldReturn404ForProcessThatCannotBeStarted() throws Exception {
    final var result = startProcessDeployInvokeAndReturn("simple_process.bpmn", "Process_1g4wt4m");
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  public void shouldReturn404ForProcessThatDoesNotExist() throws Exception {
    final var result = startProcessDeployInvokeAndReturn("simple_process.bpmn", "wrongProcess");
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .extractingErrorContent(objectMapper)
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasMessage("Could not find process with id 'wrongProcess'.");
  }

  private MockHttpServletResponse startProcessDeployInvokeAndReturn(
      final String pathProcess, final String bpmnProcessId) throws Exception {
    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("\"testValue\""));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("\"testValue2\""));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, pathProcess);

    databaseTestExtension.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    return mockMvcHelper.doRequest(
        patch(TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/start"), bpmnProcessId)
            .content(objectMapper.writeValueAsString(startProcessRequest))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8.name()));
  }
}
