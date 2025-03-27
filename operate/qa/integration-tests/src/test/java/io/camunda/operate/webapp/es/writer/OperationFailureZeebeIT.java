/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.es.writer;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesQuery;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ProcessInstanceStateDto;
import io.camunda.operate.webapp.writer.ProcessInstanceSource;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.entities.operation.OperationType;
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
    cancelProcessInstanceHandler.setCamundaClient(super.getClient());
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
