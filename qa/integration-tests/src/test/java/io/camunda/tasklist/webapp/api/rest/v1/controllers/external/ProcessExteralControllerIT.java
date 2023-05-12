/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static io.camunda.tasklist.util.assertions.CustomAssertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.ElasticsearchChecks;
import io.camunda.tasklist.util.MockMvcHelper;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

public class ProcessExteralControllerIT extends TasklistZeebeIntegrationTest {

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private ElasticsearchChecks.TestCheck processIsDeployedCheck;

  @Autowired private WebApplicationContext context;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private TasklistProperties tasklistProperties;

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
}
