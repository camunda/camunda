/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.webapp.es.writer;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.writer.ProcessInstanceSource;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public class OperationFailureZeebeIT extends OperateZeebeAbstractIT {

  private static final String QUERY_INSTANCES_URL = PROCESS_INSTANCE_URL;

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @SpyBean private io.camunda.operate.webapp.writer.BatchOperationWriter batchOperationWriter;

  @Autowired private BatchOperationTemplate batchOperationTemplate;

  @Override
  @Before
  public void before() {
    super.before();
    cancelProcessInstanceHandler.setZeebeClient(super.getClient());
    mockMvc = mockMvcTestRule.getMockMvc();
    tester.deployProcess("demoProcess_v_2.bpmn");
  }

  @Ignore
  @Test
  public void testCancelExecutedEvenThoughBatchOperationNotFullyPersisted() throws Exception {
    // given
    final ProcessInstanceSource processInstanceSource1 = startDemoProcessInstance();
    final ProcessInstanceSource processInstanceSource2 = startDemoProcessInstance();

    // first single operation will be created successfully, second will fail
    /*  doCallRealMethod().when(batchOperationWriter)
            .getIndexOperationRequest(eq(processInstanceSource1), any(), any(), any());
        IndexRequest failingRequest = new IndexRequest(batchOperationTemplate.getFullQualifiedName()).id("id")
            .source("{\"wrong_field\":\"\"}", XContentType.JSON);
        doReturn(failingRequest).when(batchOperationWriter)
            .getIndexOperationRequest(eq(processInstanceSource2), any(), any(), any());
    */
    // when
    // we call CANCEL_PROCESS_INSTANCE operation on instance
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(
                Arrays.asList(
                    processInstanceSource1.getProcessInstanceKey().toString(),
                    processInstanceSource2.getProcessInstanceKey().toString()));
    try {
      postBatchOperation(processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE, null, 500);
    } catch (final Exception ex) {
      // expected
    }
    searchTestRule.refreshSerchIndexes();
    // and execute the operation
    executeOneBatch();

    // then
    // import works without being stuck on empty batch operation
    searchTestRule.processAllRecordsAndWait(
        processInstanceIsCanceledCheck, processInstanceSource1.getProcessInstanceKey());
    final ListViewResponseDto processInstances = getProcessInstances(processInstanceQuery);
    assertThat(processInstances.getProcessInstances()).hasSize(2);
    assertThat(processInstances.getProcessInstances())
        .extracting(
            pi -> {
              return pi.getState();
            })
        .containsExactlyInAnyOrder(
            ProcessInstanceStateDto.ACTIVE, ProcessInstanceStateDto.CANCELED);
  }

  private ProcessInstanceSource startDemoProcessInstance() {
    final String processId = "demoProcess";
    tester.startProcessInstance(processId, "{\"a\": \"b\"}").waitUntil().flowNodeIsActive("taskA");

    return new ProcessInstanceSource()
        .setProcessInstanceKey(tester.getProcessInstanceKey())
        .setProcessDefinitionKey(tester.getProcessDefinitionKey())
        .setBpmnProcessId(processId);
  }

  private ListViewResponseDto getProcessInstances(final ListViewQueryDto query) throws Exception {
    final ListViewRequestDto request = new ListViewRequestDto(query);
    request.setPageSize(100);
    final MockHttpServletRequestBuilder getProcessInstancesRequest =
        post(query())
            .content(mockMvcTestRule.json(request))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc
            .perform(getProcessInstancesRequest)
            .andExpect(status().isOk())
            .andExpect(content().contentType(mockMvcTestRule.getContentType()))
            .andReturn();

    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
  }

  private String query() {
    return QUERY_INSTANCES_URL;
  }
}
