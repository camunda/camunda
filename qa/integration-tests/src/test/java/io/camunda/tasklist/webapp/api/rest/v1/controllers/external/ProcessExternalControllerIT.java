/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.util.ElasticsearchChecks;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessExternalControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private ElasticsearchChecks.TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  private MockMvcHelper mockMvcHelper;

  @Before
  public void setUp() {
    mockMvcHelper =
        new MockMvcHelper(MockMvcBuilders.webAppContextSetup(context).build(), objectMapper);
  }

  @Test
  public void getFormByProcessId() {
    // given
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "startedByFormProcess.bpmn");
    final String bpmnProcessId = "startedByForm";
    final String formId = "testForm";

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

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
  public void shouldReturn404ToProcessThatDoesntExist() {
    final String bpmnProcessId = "processDoesntExist";
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
  public void shouldReturn404ToProcessThatCannotBeStarted() {
    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, "simple_process.bpmn");
    final String bpmnProcessId = "Process_1g4wt4m";

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

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
  }

  @Test
  public void shouldReturn404ForProcessThatCannotBeStarted() throws Exception {
    final var result = startProcessDeployInvokeAndReturn("simple_process.bpmn", "Process_1g4wt4m");
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .extractingContent(objectMapper, Error.class)
        .satisfies(
            error -> {
              Assertions.assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            });
  }

  @Test
  public void shouldReturn404ForProcessThatDoesNotExist() throws Exception {
    final var result = startProcessDeployInvokeAndReturn("simple_process.bpmn", "wrongProcess");
    assertThat(result)
        .hasHttpStatus(HttpStatus.NOT_FOUND)
        .extractingContent(objectMapper, Error.class)
        .satisfies(
            error -> {
              Assertions.assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            });
  }

  private MockHttpServletResponse startProcessDeployInvokeAndReturn(
      final String pathProcess, final String bpmnProcessId) throws Exception {
    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("\"testValue\""));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("\"testValue2\""));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    final String processId1 = ZeebeTestUtil.deployProcess(zeebeClient, pathProcess);

    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId1);

    // when
    final var result =
        mockMvcHelper.doRequest(
            patch(
                    TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/start"),
                    bpmnProcessId)
                .content(objectMapper.writeValueAsString(startProcessRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8.name()));

    return result;
  }
}
