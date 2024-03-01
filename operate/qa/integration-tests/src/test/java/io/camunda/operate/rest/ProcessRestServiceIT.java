/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
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

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ProcessRestService.class,
      OperateProperties.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class
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
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
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
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.READ))
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
        .thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE))
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
        .thenReturn(new ProcessEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.hasPermissionForProcess(bpmnProcessId, IdentityPermission.DELETE))
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
