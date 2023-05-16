/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.es.FormReader;
import io.camunda.tasklist.webapp.es.cache.ProcessReader;
import io.camunda.tasklist.webapp.graphql.entity.FormDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessDTO;
import io.camunda.tasklist.webapp.graphql.entity.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.graphql.entity.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.NotFoundException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.ProcessService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
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
public class ProcessExternalControllerTest {

  private MockMvc mockMvc;
  @Mock private ProcessReader processReader;

  @Mock private ProcessService processService;

  @Mock private FormReader formReader;

  @InjectMocks private ProcessExternalController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void getForm() throws Exception {
    final var bpmnProcessId = "hello";
    final var providedProcessDTO =
        new ProcessDTO()
            .setId("2251799813686367")
            .setProcessDefinitionId("hello")
            .setName("")
            .setVersion(1)
            .setFormKey("camunda-forms:bpmn:testForm")
            .setStartedByForm(true);
    final var expectedFormResponse =
        new FormResponse()
            .setId("testForm")
            .setProcessDefinitionKey("hello")
            .setSchema("formSchema")
            .setProcessDefinitionKey("2251799813686367");

    final var formDTO =
        new FormDTO()
            .setId("testForm")
            .setProcessDefinitionId("2251799813686367")
            .setSchema("formSchema");

    when(processReader.getProcessByBpmnProcessId(bpmnProcessId)).thenReturn(providedProcessDTO);
    when(formReader.getFormDTO("testForm", providedProcessDTO.getId())).thenReturn(formDTO);

    final var responseAsString =
        mockMvc
            .perform(
                get(
                    TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                    bpmnProcessId))
            .andDo(print())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, FormResponse.class);

    // then
    assertThat(result).isEqualTo(expectedFormResponse);
  }

  @Test
  public void getFormWhenProcessCannotBeStarted() throws Exception {
    final var bpmnProcessId = "orderProcess";
    final var providedProcessDTO =
        new ProcessDTO()
            .setId("2251799813686367")
            .setProcessDefinitionId("orderProcess")
            .setName("")
            .setVersion(1)
            .setStartedByForm(false);

    when(processReader.getProcessByBpmnProcessId(bpmnProcessId)).thenReturn(providedProcessDTO);

    mockMvc
        .perform(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId))
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  public void getFormWhenProcessDoesntExist() throws Exception {
    final var bpmnProcessId = "orderProcess";
    final var providedProcessDTO =
        new ProcessDTO()
            .setId("2251799813686367")
            .setProcessDefinitionId("orderProcess")
            .setName("")
            .setVersion(1)
            .setStartedByForm(false);

    when(processReader.getProcessByBpmnProcessId(bpmnProcessId))
        .thenThrow(new TasklistRuntimeException("Object not found"));

    mockMvc
        .perform(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId))
        .andDo(print())
        .andExpect(status().is4xxClientError())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static Stream<Arguments> startProcessByForm() throws Exception {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessDTO providedProcessDTO =
        new ProcessDTO()
            .setProcessDefinitionId(bpmnProcessId)
            .setId("1")
            .setStartedByForm(true)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormKey("camundaForm:bpmn:startForm");
    final Long processInstanceId = RandomUtils.nextLong();
    final ProcessInstanceDTO processInstanceDTO = new ProcessInstanceDTO().setId(processInstanceId);

    return Stream.of(
        Arguments.of(HttpStatus.OK, bpmnProcessId, providedProcessDTO, processInstanceDTO));
  }

  private static Stream<Arguments> startProcessThatCannotBeStartedByForm() {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessDTO processDTO =
        new ProcessDTO()
            .setProcessDefinitionId(bpmnProcessId)
            .setId("1")
            .setStartedByForm(false)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormKey("camundaForm:bpmn:startForm");

    return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, bpmnProcessId, processDTO, null));
  }

  private static Stream<Arguments> startProcessThatDoesNotExist() {
    return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, "doesntExist", null, null));
  }

  @ParameterizedTest
  @MethodSource({
    "startProcessByForm",
    "startProcessThatCannotBeStartedByForm",
    "startProcessThatDoesNotExist"
  })
  public void startProcess(
      final HttpStatus expectedHttpStatus,
      final String bpmnProcessId,
      final ProcessDTO providedProcessDTO,
      final ProcessInstanceDTO providedProcessInstanceDTO)
      throws Exception {
    final Boolean isProcessThatDoesNotExist = providedProcessDTO == null;

    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    if (isProcessThatDoesNotExist) {
      when(processReader.getProcessByBpmnProcessId(bpmnProcessId))
          .thenThrow(new NotFoundException("Could not find process with id."));
    } else {
      when(processReader.getProcessByBpmnProcessId(providedProcessDTO.getProcessDefinitionId()))
          .thenReturn(providedProcessDTO);
    }

    if (providedProcessInstanceDTO != null) {
      when(processService.startProcessInstance(bpmnProcessId, variables))
          .thenReturn(providedProcessInstanceDTO);
    }

    final String responseAsString =
        mockMvc
            .perform(
                patch(
                        TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/start"),
                        bpmnProcessId)
                    .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(startProcessRequest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .characterEncoding(StandardCharsets.UTF_8.name()))
            .andDo(print())
            .andExpect(
                expectedHttpStatus.is2xxSuccessful()
                    ? status().isOk()
                    : status().is4xxClientError())
            .andReturn()
            .getResponse()
            .getContentAsString();

    if (expectedHttpStatus.is2xxSuccessful()) {
      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(responseAsString, ProcessInstanceDTO.class);
      assertThat(result).isEqualTo(providedProcessInstanceDTO);
    } else {
      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, Error.class);
      assertThat(result.getStatus()).isEqualTo(expectedHttpStatus.value());
    }
  }
}
