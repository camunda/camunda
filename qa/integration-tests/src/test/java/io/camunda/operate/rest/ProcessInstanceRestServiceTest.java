/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.es.reader.ActivityStatisticsReader;
import io.camunda.operate.webapp.es.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.es.reader.OperationReader;
import io.camunda.operate.webapp.es.reader.SequenceFlowReader;
import io.camunda.operate.webapp.es.reader.VariableReader;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import javax.validation.ConstraintViolationException;

import static org.mockito.Mockito.when;

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

  @MockBean
  private OperationReader operationReader;

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

  @Test
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getInstanceByIdUrl("4503599627535750:"));
  }

  @Test
  public void testGetInstanceByIdWithIdValueNull() throws Exception {
    getRequestShouldFailValidationForUrl(getInstanceByIdUrl("null"));
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getIncidentsByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetSequenceFlowsByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getSequenceFlowsByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    postRequestShouldFailValidationForUrl(getVariablesByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    getRequestShouldFailValidationForUrl(getFlowNodeStatesByIdUrl("not-valid-id-123"));
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    postRequestShouldFailValidationForUrl(getFlowNodeMetadataByIdUrl("not-valid-id-123"));
  }

  private void getRequestShouldFailValidationForUrl(String url) throws Exception {
    MvcResult mvcResult = getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  private void postRequestShouldFailValidationForUrl(String url) throws Exception {
    MvcResult mvcResult = postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertErrorMessageContains(mvcResult, "Specified ID is not valid");
  }

  @Test
  public void testGetInstanceByIdWithValidId() throws Exception {
    // given
    String validId = "123";
    // when
    ListViewProcessInstanceDto expectedDto = new ListViewProcessInstanceDto().setId("one id");
    when(processInstanceReader.getProcessInstanceWithOperationsByKey(123L))
        .thenReturn(expectedDto);
    MvcResult mvcResult = getRequest(getInstanceByIdUrl(validId));
    // then
    ListViewProcessInstanceDto actualResult = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    Assert.assertEquals(expectedDto, actualResult);
  }

  public String getBatchOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/batch-operation";
  }

  public String getOperationUrl() {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/111/operation";
  }

  public String getInstanceByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id;
  }

  public String getIncidentsByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/incidents";
  }

  public String getSequenceFlowsByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/sequence-flows";
  }

  public String getVariablesByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/variables";
  }

  public String getFlowNodeStatesByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-states";
  }

  public String getFlowNodeMetadataByIdUrl(String id) {
    return ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/" + id + "/flow-node-metadata";
  }

}
