/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.reader.ProcessReader;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.webapp.security.identity.IdentityPermission;
import io.camunda.operate.webapp.security.identity.PermissionsService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.*;

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
public class ProcessRestServiceTest extends OperateIntegrationTest {

  @MockBean
  protected ProcessReader processReader;

  @MockBean
  protected ProcessInstanceReader processInstanceReader;

  @MockBean
  private PermissionsService permissionsService;

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

  public String getProcessByIdUrl(String id) {
    return ProcessRestService.PROCESS_URL + "/" + id;
  }

  public String getProcessXmlByIdUrl(String id) {
    return ProcessRestService.PROCESS_URL + "/" + id + "/xml";
  }
}
