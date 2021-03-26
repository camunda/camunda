/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.rest;

import org.camunda.operate.JacksonConfig;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import org.camunda.operate.webapp.es.reader.ActivityStatisticsReader;
import org.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import org.camunda.operate.webapp.es.reader.IncidentReader;
import org.camunda.operate.webapp.es.reader.ListViewReader;
import org.camunda.operate.webapp.es.reader.SequenceFlowReader;
import org.camunda.operate.webapp.es.reader.VariableReader;
import org.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.camunda.operate.webapp.es.writer.BatchOperationWriter;
import org.camunda.operate.webapp.rest.ProcessInstanceRestService;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
  classes = {TestApplicationWithNoBeans.class, ProcessInstanceRestService.class, JacksonConfig.class, OperateProperties.class}
)
public class ProcessInstanceRestServiceTest extends OperateIntegrationTest {

  @MockBean
  private ListViewReader listViewReader;

  @MockBean
  private ActivityStatisticsReader activityStatisticsReader;

  @MockBean
  private ProcessInstanceReader processInstanceReader;

  @MockBean
  private IncidentReader incidentReader;

  @MockBean
  private VariableReader variableReader;

  @MockBean
  private SequenceFlowReader sequenceFlowReader;

  @MockBean
  private FlowNodeInstanceReader flowNodeInstanceReader;

  @MockBean
  private BatchOperationWriter batchOperationWriter;

  @Test
  public void testQueryWithWrongSortBy() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"processId\",\"sortOrder\": \"asc\"}}";     //not allowed for sorting
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortBy");
  }

  @Test
  public void testQueryWithWrongSortOrder() throws Exception {
    //when
    String jsonRequest = "{ \"sorting\": {\"sortBy\": \"id\",\"sortOrder\": \"unknown\"}}";     //wrong sort order
    final MvcResult mvcResult = postRequestThatShouldFail(query(0, 100),jsonRequest);
    //then
    assertErrorMessageContains(mvcResult, "SortOrder");
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", ProcessInstanceRestService.PROCESS_INSTANCE_URL, firstResult, maxResults);
  }

  @Test
  public void testOperationForUpdateVariableFailsNoValue() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableName("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoName() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableScopeId("a");
    operationRequestDto.setVariableValue("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationForUpdateVariableFailsNoScopeId() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    operationRequestDto.setVariableName("a");
    operationRequestDto.setVariableValue("a");
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "ScopeId, name and value must be defined for UPDATE_VARIABLE operation.");
  }

  @Test
  public void testOperationFailsNoOperationType() throws Exception {
    CreateOperationRequestDto operationRequestDto = new CreateOperationRequestDto();
    MvcResult mvcResult = postRequestThatShouldFail(getOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "Operation type must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsNoQuery() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(null, OperationType.UPDATE_VARIABLE);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "List view query must be defined.");
  }

  @Test
  public void testBatchOperationForUpdateVariableFailsWrongEndpoint() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(new ListViewQueryDto(), OperationType.UPDATE_VARIABLE);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "For variable update use \"Create operation for one process instance\" endpoint.");
  }

  @Test
  public void testBatchOperationFailsNoOperationType() throws Exception {
    CreateBatchOperationRequestDto operationRequestDto = new CreateBatchOperationRequestDto(new ListViewQueryDto(), null);
    MvcResult mvcResult = postRequestThatShouldFail(getBatchOperationUrl(), operationRequestDto);
    assertErrorMessageContains(mvcResult, "Operation type must be defined.");
  }

  public String getBatchOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  }

  public String getOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/111/operation";
  }

}
