/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.ProcessReader;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = {
        TestApplicationWithNoBeans.class,
        ProcessRestService.class,
        JacksonConfig.class,
        OperateProperties.class,
        OperateProfileService.class,
        JacksonConfig.class,
        OperateProperties.class
    }
)
public class ProcessRestServiceIT extends OperateAbstractIT {

  @MockBean
  protected ProcessReader processReader;

  @MockBean
  protected ProcessInstanceReader processInstanceReader;

  @MockBean
  private PermissionsService permissionsService;

  @MockBean
  private BatchOperationWriter batchOperationWriter;

  @Test
  public void testProcessDefinitionByIdFailsWhenNoPermissions() throws Exception {
    // given
    Long processDefinitionKey = 123L;
    String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey)).thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getProcessByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process");
  }

  @Test
  public void testProcessDefinitionXmlFailsWhenNoPermissions() throws Exception {
    // given
    Long processDefinitionKey = 123L;
    String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey)).thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ)).thenReturn(false);
    MvcResult mvcResult = getRequestShouldFailWithNoAuthorization(getProcessXmlByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process");
  }

  @Test
  public void testDeleteProcessDefinition() throws Exception {
    // given
    Long processDefinitionKey = 123L;
    String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey)).thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE)).thenReturn(true);
    when(batchOperationWriter.scheduleDeleteProcessDefinition(any())).thenReturn(new BatchOperationEntity());
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(getProcessByIdUrl(processDefinitionKey.toString()))
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    BatchOperationEntity batchOperationEntity = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });
    // then
    assertThat(batchOperationEntity).isNotNull();
  }

  @Test
  public void testDeleteProcessDefinitionFailsForMissingKey() throws Exception {
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(ProcessRestService.PROCESS_URL)
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request).andExpect(status().isNotFound()).andReturn();
  }

  @Test
  public void testDeleteProcessDefinitionFailsForNotExistingProcess() throws Exception {
    Long processDefinitionKey = 123L;
    when(processReader.getProcess(processDefinitionKey)).thenThrow(new NotFoundException("Not found"));
    MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete(getProcessByIdUrl(processDefinitionKey.toString()))
        .accept(mockMvcTestRule.getContentType());
    MvcResult mvcResult = mockMvc.perform(request)
        .andExpect(status().isNotFound())
        .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(NotFoundException.class))
        .andReturn();
  }

  @Test
  public void testDeleteProcessDefinitionFailsWhenNoPermissions() throws Exception {
    // given
    Long processDefinitionKey = 123L;
    String bpmnProcessId = "processId";
    // when
    when(processReader.getProcess(processDefinitionKey)).thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE)).thenReturn(false);
    when(batchOperationWriter.scheduleDeleteProcessDefinition(any())).thenReturn(new BatchOperationEntity());
    MvcResult mvcResult = deleteRequestShouldFailWithNoAuthorization(getProcessByIdUrl(processDefinitionKey.toString()));
    // then
    assertErrorMessageContains(mvcResult, "No delete permission for process");
  }

  public String getProcessByIdUrl(String id) {
    return ProcessRestService.PROCESS_URL + "/" + id;
  }

  public String getProcessXmlByIdUrl(String id) {
    return ProcessRestService.PROCESS_URL + "/" + id + "/xml";
  }
}
