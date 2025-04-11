/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.controllers.internal;

import static io.camunda.client.api.command.CommandWithTenantStep.DEFAULT_TENANT_IDENTIFIER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.property.FeatureFlagProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.store.FormStore;
import io.camunda.tasklist.store.ProcessStore;
import io.camunda.tasklist.webapp.CommonUtils;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessPublicEndpointsResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.ProcessResponse;
import io.camunda.tasklist.webapp.api.rest.v1.entities.StartProcessRequest;
import io.camunda.tasklist.webapp.dto.ProcessInstanceDTO;
import io.camunda.tasklist.webapp.dto.VariableInputDTO;
import io.camunda.tasklist.webapp.permission.TasklistPermissionServices;
import io.camunda.tasklist.webapp.rest.exception.Error;
import io.camunda.tasklist.webapp.rest.exception.ForbiddenActionException;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import io.camunda.tasklist.webapp.rest.exception.NotFoundApiException;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import io.camunda.tasklist.webapp.service.ProcessService;
import io.camunda.tasklist.webapp.tenant.TenantService;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProcessInternalControllerTest {
  @Mock private ProcessStore processStore;
  @Mock private FormStore formStore;
  @Mock private ProcessService processService;
  @Mock private TasklistProperties tasklistProperties;
  @Mock private TenantService tenantService;
  @Mock private TasklistPermissionServices permissionServices;

  @InjectMocks private ProcessInternalController instance;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.standaloneSetup(instance).build();
  }

  @Nested
  class StartAndDeleteProcessInstancesTests {

    @Test
    void startProcessInstance() throws Exception {
      // given
      final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
      variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
      variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));

      final var processDefinitionKey = "key1";
      final var processInstanceDTO = new ProcessInstanceDTO().setId(124L);

      final StartProcessRequest startProcessRequest =
          new StartProcessRequest().setVariables(variables);
      when(processService.startProcessInstance(processDefinitionKey, variables, null))
          .thenReturn(processInstanceDTO);

      // when
      final var responseAsString =
          mockMvc
              .perform(
                  patch(
                          TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                          processDefinitionKey)
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(startProcessRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .characterEncoding(StandardCharsets.UTF_8.name()))
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
    void startProcessInstanceWhenProcessNotFoundByProcessDefinitionKeyThen404ErrorExpected()
        throws Exception {
      // given
      final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
      variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
      variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));

      final var processDefinitionKey = "unknown_key1";

      final var expectedMessage =
          "No process definition found with processDefinitionKey: " + processDefinitionKey;

      final StartProcessRequest startProcessRequest =
          new StartProcessRequest().setVariables(variables);
      when(processService.startProcessInstance(processDefinitionKey, variables, null))
          .thenThrow(new NotFoundApiException(expectedMessage));

      // when
      final var responseAsString =
          mockMvc
              .perform(
                  patch(
                          TasklistURIs.PROCESSES_URL_V1.concat("/{processDefinitionKey}/start"),
                          processDefinitionKey)
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(startProcessRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .characterEncoding(StandardCharsets.UTF_8.name()))
              .andDo(print())
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // then
      assertThat(responseAsString).contains(expectedMessage);
    }

    @Test
    void startProcessInstanceInvalidTenantId() throws Exception {
      final var processDefinitionKey = "key1";
      final List<VariableInputDTO> variables = new ArrayList<VariableInputDTO>();
      variables.add(new VariableInputDTO().setName("testVar").setValue("testValue"));
      variables.add(new VariableInputDTO().setName("testVar2").setValue("testValue2"));
      final var processInstanceDTO = new ProcessInstanceDTO().setId(124L);
      final var tenantId = "TenantA";

      final StartProcessRequest startProcessRequest =
          new StartProcessRequest().setVariables(variables);
      when(processService.startProcessInstance(processDefinitionKey, variables, tenantId))
          .thenThrow(new InvalidRequestException("Invalid tenant"));
      final var responseAsString =
          mockMvc
              .perform(
                  patch(
                          TasklistURIs.PROCESSES_URL_V1.concat(
                              "/{processDefinitionKey}/start?tenantId={tenantId}"),
                          processDefinitionKey,
                          tenantId)
                      .content(CommonUtils.OBJECT_MAPPER.writeValueAsString(startProcessRequest))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .characterEncoding(StandardCharsets.UTF_8.name()))
              .andDo(print())
              .andExpect(status().is4xxClientError());
    }
  }

  @Nested
  class SearchProcessesTests {
    @Test
    void searchProcesses() throws Exception {
      // given
      final var query = "search 123";
      final var providedProcessEntity =
          new ProcessEntity()
              .setId("2251799813685257")
              .setName("Register car for rent")
              .setBpmnProcessId("registerCarForRent")
              .setVersion(1)
              .setFormKey("camunda-forms:bpmn:userTaskForm_111")
              .setIsPublic(true);

      final var expectedProcessResponse =
          new ProcessResponse()
              .setId("2251799813685257")
              .setName("Register car for rent")
              .setBpmnProcessId("registerCarForRent")
              .setVersion(1)
              .setStartEventFormId("task")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);
      when(permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission())
          .thenReturn(new ArrayList<>());
      when(processStore.getProcesses(
              query,
              permissionServices.getProcessDefinitionsWithCreateProcessInstancePermission(),
              null,
              null))
          .thenReturn(List.of(providedProcessEntity));
      when(formStore.getForm("userTaskForm_111", "2251799813685257", null))
          .thenReturn(new FormEntity().setId("task").setFormId("task"));

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
  }

  @Nested
  class GetPublicEndpointsTests {
    @Test
    void getPublicEndpoints() throws Exception {
      // given

      final var processEntity =
          new ProcessEntity()
              .setId("1")
              .setFormKey("camunda:bpmn:publicForm")
              .setBpmnProcessId("publicProcess")
              .setVersion(1)
              .setName("publicProcess")
              .setIsPublic(true);

      final var expectedEndpointsResponse =
          new ProcessPublicEndpointsResponse()
              .setEndpoint(TasklistURIs.START_PUBLIC_PROCESS.concat("publicProcess"))
              .setBpmnProcessId("publicProcess")
              .setProcessDefinitionKey("1")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);

      final var expectedFeatureFlag = new FeatureFlagProperties().setProcessPublicEndpoints(true);

      when(processStore.getProcessesStartedByForm()).thenReturn(List.of(processEntity));
      when(tasklistProperties.getFeatureFlag()).thenReturn(expectedFeatureFlag);
      when(processStore.getProcessesStartedByForm()).thenReturn(List.of(processEntity));

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

    @Test
    void getPublicEndpointsByBpmnProcessId() throws Exception {
      // given
      final String processDefinitionKey = "publicProcess";

      final var processEntity =
          new ProcessEntity()
              .setId("1")
              .setFormKey("camunda:bpmn:publicForm")
              .setBpmnProcessId("publicProcess")
              .setVersion(1)
              .setName("publicProcess")
              .setIsPublic(true);

      final var expectedEndpointsResponse =
          new ProcessPublicEndpointsResponse()
              .setEndpoint(TasklistURIs.START_PUBLIC_PROCESS.concat("publicProcess"))
              .setBpmnProcessId("publicProcess")
              .setProcessDefinitionKey("1")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);

      when(tenantService.isTenantValid(null)).thenReturn(true);
      when(processStore.getProcessByBpmnProcessId(processDefinitionKey, null))
          .thenReturn(processEntity);

      // when
      final var responseAsString =
          mockMvc
              .perform(
                  get(
                      TasklistURIs.PROCESSES_URL_V1.concat(
                          "/{processDefinitionKey}/publicEndpoint"),
                      processDefinitionKey))
              .andDo(print())
              .andExpect(content().contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result =
          CommonUtils.OBJECT_MAPPER.readValue(
              responseAsString, new TypeReference<ProcessPublicEndpointsResponse>() {});

      // then
      assertThat(result).isEqualTo(expectedEndpointsResponse);
    }

    @Test
    void getPublicEndpointsByBpmnProcessIdWhenTenantIdIsInvalid() throws Exception {
      // given
      final String processDefinitionKey = "publicProcess";

      when(tenantService.isTenantValid("tenant_a")).thenReturn(false);

      // when
      final var responseAsString =
          mockMvc
              .perform(
                  get(
                          TasklistURIs.PROCESSES_URL_V1.concat(
                              "/{processDefinitionKey}/publicEndpoint"),
                          processDefinitionKey)
                      .queryParam("tenantId", "tenant_a"))
              .andDo(print())
              .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      final var result = CommonUtils.OBJECT_MAPPER.readValue(responseAsString, Error.class);

      // then
      assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(result.getMessage()).isEqualTo("Invalid Tenant");
    }
  }

  @Nested
  class GetProcessTests {
    @Test
    void getProcess() throws Exception {
      final var processDefinitionKey = "225599880022";
      final var providedProcessEntity =
          new ProcessEntity()
              .setId(processDefinitionKey)
              .setName("Register car for rent")
              .setBpmnProcessId("registerCarForRent")
              .setVersion(1)
              .setFormKey("camunda-forms:bpmn:userTaskForm_111")
              .setBpmnXml("<abc></abc>")
              .setIsPublic(true)
              .setIsFormEmbedded(false)
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);

      final var expectedProcessReturn =
          new ProcessResponse()
              .setId(processDefinitionKey)
              .setName("Register car for rent")
              .setBpmnProcessId("registerCarForRent")
              .setVersion(1)
              .setBpmnXml("<abc></abc>")
              .setTenantId(DEFAULT_TENANT_IDENTIFIER);

      when(processService.getProcessByProcessDefinitionKeyAndAccessRestriction(
              processDefinitionKey))
          .thenReturn(providedProcessEntity);
      final var response =
          mockMvc
              .perform(
                  get(
                      TasklistURIs.PROCESSES_URL_V1.concat(
                          String.format("/%s", processDefinitionKey))))
              .andDo(print())
              .andReturn()
              .getResponse();
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
      final ProcessResponse responseObject =
          CommonUtils.OBJECT_MAPPER.readValue(
              response.getContentAsString(), new TypeReference<ProcessResponse>() {});
      assertThat(responseObject).isEqualTo(expectedProcessReturn);
    }

    @Test
    void getProcessShouldReturn403() throws Exception {
      final String processDefinitionKey = "shouldReturn403";
      final String errorMessage = "Resource cannot be accessed";
      when(processService.getProcessByProcessDefinitionKeyAndAccessRestriction(
              processDefinitionKey))
          .thenThrow(new ForbiddenActionException(errorMessage));
      final var response =
          mockMvc
              .perform(
                  get(
                      TasklistURIs.PROCESSES_URL_V1.concat(
                          String.format("/%s", processDefinitionKey))))
              .andDo(print())
              .andReturn()
              .getResponse();

      assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
      assertThat(response.getContentAsString()).contains(errorMessage);
    }

    @Test
    void getProcessShouldReturn404() throws Exception {
      final String processDefinitionKey = "shouldReturn404";
      final String errorMessage =
          String.format("Process with key %s not found", processDefinitionKey);
      when(processService.getProcessByProcessDefinitionKeyAndAccessRestriction(
              processDefinitionKey))
          .thenThrow(new NotFoundException(errorMessage));
      final var response =
          mockMvc
              .perform(
                  get(
                      TasklistURIs.PROCESSES_URL_V1.concat(
                          String.format("/%s", processDefinitionKey))))
              .andDo(print())
              .andReturn()
              .getResponse();

      assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(response.getContentAsString()).contains(errorMessage);
    }
  }
}
