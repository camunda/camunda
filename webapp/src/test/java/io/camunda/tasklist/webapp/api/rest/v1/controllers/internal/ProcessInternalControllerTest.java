/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.es.ProcessInstanceWriter;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.es.enums.DeletionStatus;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.ProcessService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProcessInternalControllerTest {

  @Mock private ProcessReader processReader;
  @Mock private ProcessService processService;
  @Mock private ProcessInstanceWriter processInstanceWriter;

  @InjectMocks private ProcessInternalController instance;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void searchProcesses() throws Exception {
    // given
    final var query = "search 123";
    final var providedProcessDTO =
        new ProcessDTO()
            .setId("2251799813685257")
            .setName("Register car for rent")
            .setProcessDefinitionId("registerCarForRent")
            .setSortValues(new String[] {"1"})
            .setVersion(1);
    final var expectedProcessResponse =
        new ProcessResponse()
            .setId("2251799813685257")
            .setName("Register car for rent")
            .setProcessDefinitionKey("registerCarForRent")
            .setSortValues(new String[] {"1"})
            .setVersion(1);
    when(processReader.getProcesses(query)).thenReturn(List.of(providedProcessDTO));

    // when
    final var responseAsString =
        mockMvc
            .perform(get(TasklistURIs.PROCESSES_URL_V1).param("query", query))
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<ProcessResponse>>() {});

    // then
    assertThat(result).containsExactly(expectedProcessResponse);
  }

  @Test
  void startProcessInstance() throws Exception {
    // given
    final var processDefinitionKey = "key1";
    final var processInstanceDTO = new ProcessInstanceDTO().setId(124L);
    when(processService.startProcessInstance(processDefinitionKey)).thenReturn(processInstanceDTO);

    // when
    final var responseAsString =
        mockMvc
            .perform(
                patch(
                    TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                    processDefinitionKey))
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(responseAsString, ProcessInstanceDTO.class);

    // then
    assertThat(result).isEqualTo(processInstanceDTO);
  }

  @Test
  void deleteProcess() throws Exception {
    // given
    final var processInstanceId = "225599880022";
    when(processInstanceWriter.deleteProcessInstance(processInstanceId))
        .thenReturn(DeletionStatus.DELETED);

    // when
    final var result =
        mockMvc
            .perform(
                delete(
                    TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"),
                    processInstanceId))
            .andDo(print())
            .andReturn()
            .getResponse();

    // then
    assertThat(result.getContentAsString()).isEmpty();
    assertThat(result.getStatus()).isEqualTo(HttpStatus.NO_CONTENT.value());
  }

  private static Stream<Arguments> deleteProcessExceptionTestData() {
    return Stream.of(
        Arguments.of(
            DeletionStatus.FAILED,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "The deletion of process with processInstanceId: '%s' could not be deleted"),
        Arguments.of(
            DeletionStatus.NOT_FOUND,
            HttpStatus.NOT_FOUND,
            "The process with processInstanceId: '%s' is not found"));
  }

  @ParameterizedTest
  @MethodSource("deleteProcessExceptionTestData")
  void deleteProcessWhenDeleteWasNotSuccessfulThenExceptionExpected(
      DeletionStatus deletionStatus, HttpStatus expectedHttpStatus, String errorMessageTemplate)
      throws Exception {
    // given
    final var processInstanceId = "225599880033";
    when(processInstanceWriter.deleteProcessInstance(processInstanceId)).thenReturn(deletionStatus);

    // when
    final var errorResponseAsString =
        mockMvc
            .perform(
                delete(
                    TasklistURIs.PROCESSES_URL_V1.concat("/{processInstanceId}"),
                    processInstanceId))
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result = CommonUtils.OBJECT_MAPPER.readValue(errorResponseAsString, Error.class);

    // then
    assertThat(result.getStatus()).isEqualTo(expectedHttpStatus.value());
    assertThat(result.getMessage()).isEqualTo(errorMessageTemplate, processInstanceId);
  }

  @Test
  void getPublicEndpoints() throws Exception {
    // given

    final var processDto =
        new ProcessDTO()
            .setId("1")
            .setFormKey("camunda:bpmn:publicForm")
            .setProcessDefinitionId("publicProcess")
            .setVersion(1)
            .setName("publicProcess")
            .setStartedByForm(true);

    final var expectedEndpointsResponse =
        new ProcessPublicEndpointsResponse()
            .setEndpoint(TasklistURIs.START_PUBLIC_PROCESS.concat("publicProcess"))
            .setProcessId("1")
            .setProcessDefinitionKey("publicProcess");

    when(processReader.getProcessesStartedByForm()).thenReturn(List.of(processDto));

    // when
    final var responseAsString =
        mockMvc
            .perform(get(TasklistURIs.PROCESSES_URL_V1.concat("/publicEndpoints")))
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    final var result =
        CommonUtils.OBJECT_MAPPER.readValue(
            responseAsString, new TypeReference<List<ProcessPublicEndpointsResponse>>() {});

    // then
    assertThat(result).containsExactly(expectedEndpointsResponse);
  }
}
