/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.external;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.FormResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.dto.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomUtils;
import org.assertj.core.api.Assertions;
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
  @Mock private ProcessStore processStore;

  @Mock private ProcessService processService;

  @Mock private FormStore formStore;

  @Mock private TenantService tenantService;

  @InjectMocks private ProcessExternalController instance;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Test
  void getFormEmbedded() throws Exception {
    final var bpmnProcessId = "hello";
    final var processEntity =
        new ProcessEntity()
            .setId("2251799813686367")
            .setBpmnProcessId("hello")
            .setName("Process Name")
            .setVersion(1)
            .setFormKey("camunda-forms:bpmn:testForm")
            .setIsPublic(true)
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);
    final var expectedFormResponse =
        new FormResponse()
            .setId("testForm")
            .setProcessDefinitionKey("hello")
            .setSchema("formSchema")
            .setIsDeleted(false)
            .setProcessDefinitionKey("2251799813686367")
            .setTitle("Process Name")
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);

    final var formEntity =
        new FormEntity()
            .setId("2251799813686367_testForm")
            .setFormId("testForm")
            .setEmbedded(true)
            .setIsDeleted(false)
            .setProcessDefinitionId("2251799813686367")
            .setSchema("formSchema")
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);

    when(processStore.getProcessByBpmnProcessId(bpmnProcessId)).thenReturn(processEntity);
    when(formStore.getForm("testForm", processEntity.getId(), null)).thenReturn(formEntity);

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
  void getFormLinked() throws Exception {
    final var bpmnProcessId = "hello";
    final var processEntity =
        new ProcessEntity()
            .setId("2251799813686367")
            .setBpmnProcessId("hello")
            .setName("Process Name")
            .setVersion(1)
            .setFormId("testForm")
            .setIsFormEmbedded(false)
            .setIsPublic(true)
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);
    final var expectedFormResponse =
        new FormResponse()
            .setId("testForm")
            .setProcessDefinitionKey("hello")
            .setSchema("formSchema")
            .setProcessDefinitionKey("2251799813686367")
            .setTitle("Process Name")
            .setIsDeleted(false)
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);

    final var formEntity =
        new FormEntity()
            .setId("2251799813686367")
            .setFormId("testForm")
            .setEmbedded(true)
            .setProcessDefinitionId(null)
            .setSchema("formSchema")
            .setIsDeleted(false)
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);

    when(processStore.getProcessByBpmnProcessId(bpmnProcessId)).thenReturn(processEntity);
    when(formStore.getForm("testForm", processEntity.getId(), null)).thenReturn(formEntity);

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
    final var processEntity =
        new ProcessEntity()
            .setId("2251799813686367")
            .setBpmnProcessId("orderProcess")
            .setName("")
            .setVersion(1)
            .setIsPublic(false);

    when(processStore.getProcessByBpmnProcessId(bpmnProcessId)).thenReturn(processEntity);

    mockMvc
        .perform(
            get(
                TasklistURIs.EXTERNAL_PROCESS_URL_V1.concat("/{bpmnProcessId}/form"),
                bpmnProcessId))
        .andDo(print())
        .andExpect(status().is4xxClientError());
  }

  @Test
  public void getFormWhenProcessDoesntExist() throws Exception {
    final var bpmnProcessId = "orderProcess";

    when(processStore.getProcessByBpmnProcessId(bpmnProcessId))
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

  private static Stream<Arguments> startProcessByFormEmbedded() {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessEntity providedProcessEntity =
        new ProcessEntity()
            .setBpmnProcessId(bpmnProcessId)
            .setId("1")
            .setIsPublic(true)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormKey("camundaForm:bpmn:startForm")
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);
    final Long processInstanceId = RandomUtils.nextLong();
    final ProcessInstanceDTO processInstanceDTO = new ProcessInstanceDTO().setId(processInstanceId);

    return Stream.of(
        Arguments.of(HttpStatus.OK, bpmnProcessId, providedProcessEntity, processInstanceDTO));
  }

  private static Stream<Arguments> startProcessByFormLinked() {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessEntity providedProcessEntity =
        new ProcessEntity()
            .setBpmnProcessId(bpmnProcessId)
            .setId("1")
            .setIsPublic(true)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormId("startForm")
            .setIsFormEmbedded(false)
            .setTenantId(DEFAULT_TENANT_IDENTIFIER);
    final Long processInstanceId = RandomUtils.nextLong();
    final ProcessInstanceDTO processInstanceDTO = new ProcessInstanceDTO().setId(processInstanceId);

    return Stream.of(
        Arguments.of(HttpStatus.OK, bpmnProcessId, providedProcessEntity, processInstanceDTO));
  }

  private static Stream<Arguments> startProcessThatCannotBeStartedByFormEmbedded() {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setBpmnProcessId(bpmnProcessId)
            .setId("1")
            .setIsPublic(false)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormKey("camundaForm:bpmn:startForm")
            .setIsFormEmbedded(true);

    return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, bpmnProcessId, processEntity, null));
  }

  private static Stream<Arguments> startProcessThatCannotBeStartedByFormLinked() {
    final String bpmnProcessId = "StartProcessByForm";
    final ProcessEntity processEntity =
        new ProcessEntity()
            .setBpmnProcessId(bpmnProcessId)
            .setId("1")
            .setIsPublic(false)
            .setName("StartFormProcess")
            .setVersion(1)
            .setFormId("startForm")
            .setIsFormEmbedded(false);

    return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, bpmnProcessId, processEntity, null));
  }

  private static Stream<Arguments> startProcessThatDoesNotExist() {
    return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, "doesntExist", null, null));
  }

  @ParameterizedTest
  @MethodSource({
    "startProcessByFormEmbedded",
    "startProcessThatCannotBeStartedByFormEmbedded",
    "startProcessThatDoesNotExist"
  })
  public void startProcessWithEmbeddedForm(
      final HttpStatus expectedHttpStatus,
      final String bpmnProcessId,
      final ProcessEntity processEntity,
      final ProcessInstanceDTO providedProcessInstanceDTO)
      throws Exception {
    final boolean isProcessThatDoesNotExist = processEntity == null;

    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    if (isProcessThatDoesNotExist) {
      when(processStore.getProcessByBpmnProcessId(bpmnProcessId, null))
          .thenThrow(new NotFoundApiException("Could not find process with id."));
    } else {
      when(processStore.getProcessByBpmnProcessId(processEntity.getBpmnProcessId(), null))
          .thenReturn(processEntity);
    }

    if (providedProcessInstanceDTO != null) {
      when(processService.startProcessInstance(bpmnProcessId, variables, null, false))
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

  @ParameterizedTest
  @MethodSource({
    "startProcessByFormLinked",
    "startProcessThatCannotBeStartedByFormLinked",
    "startProcessThatDoesNotExist"
  })
  public void startProcessWithLinkedForm(
      final HttpStatus expectedHttpStatus,
      final String bpmnProcessId,
      final ProcessEntity processEntity,
      final ProcessInstanceDTO providedProcessInstanceDTO)
      throws Exception {
    final boolean isProcessThatDoesNotExist = processEntity == null;

    final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
    variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
    variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));

    final StartProcessRequest startProcessRequest =
        new StartProcessRequest().setVariables(variables);

    if (isProcessThatDoesNotExist) {
      when(processStore.getProcessByBpmnProcessId(bpmnProcessId, null))
          .thenThrow(new NotFoundApiException("Could not find process with id."));
    } else {
      when(processStore.getProcessByBpmnProcessId(processEntity.getBpmnProcessId(), null))
          .thenReturn(processEntity);
    }

    if (providedProcessInstanceDTO != null) {
      when(processService.startProcessInstance(bpmnProcessId, variables, null, false))
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

  @Test
  void startProcessWithInvalidTenant() {
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "TenantA";
    final List<String> tenantIds = new ArrayList<String>();
    tenantIds.add("TenantB");
    tenantIds.add("TenantC");
    final TenantService.AuthenticatedTenants authenticatedTenants =
        TenantService.AuthenticatedTenants.assignedTenants(tenantIds);

    when(tenantService.isMultiTenancyEnabled()).thenReturn(true);
    when(tenantService.getAuthenticatedTenants()).thenReturn(authenticatedTenants);

    Assertions.assertThatThrownBy(() -> instance.startProcess(bpmnProcessId, tenantId, null))
        .isInstanceOf(InvalidRequestException.class);
  }
}
