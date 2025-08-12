/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ProcessRestService.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperatePropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      UnifiedConfiguration.class,
      UnifiedConfigurationHelper.class
    })
public class ProcessRestServiceIT extends OperateAbstractIT {

  @MockBean protected ProcessReader processReader;

  @MockBean protected ProcessInstanceReader processInstanceReader;

  @MockBean private PermissionsService permissionsService;

  @MockBean private BatchOperationWriter batchOperationWriter;

  @Test
  public void testProcessDefinitionByIdFailsWhenNoPermissions() throws Exception {
    // given
    final Long processDefinitionKey = 123L;
    final String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey))
        .thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getProcessByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process");
  }

  @Test
  public void testProcessDefinitionXmlFailsWhenNoPermissions() throws Exception {
    // given
    final Long processDefinitionKey = 123L;
    final String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey))
        .thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_DEFINITION))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(
            getProcessXmlByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process");
  }

  @Test
  public void testDeleteProcessDefinition() throws Exception {
    // given
    final Long processDefinitionKey = 123L;
    final String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey))
        .thenReturn(
            new ProcessEntity().setKey(processDefinitionKey).setBpmnProcessId(bpmnProcessId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForResource(
            processDefinitionKey, PermissionType.DELETE_PROCESS))
        .thenReturn(true);
    when(batchOperationWriter.scheduleDeleteProcessDefinition(any()))
        .thenReturn(new BatchOperationEntity());
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(getProcessByIdUrl(processDefinitionKey.toString()))
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    final BatchOperationEntity batchOperationEntity =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testDeleteProcessDefinitionFailsForMissingKey() throws Exception {
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(ProcessRestService.PROCESS_URL)
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
  }

  @Test
  public void testDeleteProcessDefinitionFailsForNotExistingProcess() throws Exception {
    final Long processDefinitionKey = 123L;
    when(processReader.getProcess(processDefinitionKey))
        .thenThrow(new NotFoundException("Not found"));
    final MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.delete(getProcessByIdUrl(processDefinitionKey.toString()))
            .accept(mockMvcTestRule.getContentType());
    final MvcResult mvcResult =
        mockMvc
            .perform(request)
            .andExpect(status().isNotFound())
            .andExpect(
                result ->
                    assertThat(result.getResolvedException()).isInstanceOf(NotFoundException.class))
            .andReturn();
  }

  @Test
  public void testDeleteProcessDefinitionFailsWhenNoPermissions() throws Exception {
    // given
    final Long processDefinitionKey = 123L;
    final String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey))
        .thenReturn(
            new ProcessEntity().setKey(processDefinitionKey).setBpmnProcessId(bpmnProcessId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForResource(
            processDefinitionKey, PermissionType.DELETE_PROCESS))
        .thenReturn(false);
    when(batchOperationWriter.scheduleDeleteProcessDefinition(any()))
        .thenReturn(new BatchOperationEntity());
    final MvcResult mvcResult =
        deleteRequestShouldFailWithNoAuthorization(
            getProcessByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No delete permission for process");
  }

  public String getProcessByIdUrl(final String id) {
    return ProcessRestService.PROCESS_URL + "/" + id;
  }

  public String getProcessXmlByIdUrl(final String id) {
    return ProcessRestService.PROCESS_URL + "/" + id + "/xml";
  }
}
