/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.webapp.es.reader.IncidentReader;
import io.camunda.operate.webapp.es.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ErrorMessagesIT extends OperateZeebeIntegrationTest{

  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Autowired
  private IncidentReader incidentReader;

  @Autowired
  private ListViewReader listViewReader;

  @Before
  public void before() {
    super.before();
    injectZeebeClientIntoOperationHandler();
  }

  private void injectZeebeClientIntoOperationHandler() {
    cancelProcessInstanceHandler.setZeebeClient(zeebeClient);
    updateRetriesHandler.setZeebeClient(zeebeClient);
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  // OPE-453
  @Test
  public void testErrorMessageIsTrimmedBeforeSave() throws Exception {
    // Given
    String errorMessageWithWhitespaces ="   Error message with white spaces   ";
    String errorMessageWithoutWhiteSpaces = "Error message with white spaces";

    // when
    Long processInstanceKey = setupIncidentWith(errorMessageWithWhitespaces);
    tester.updateVariableOperation("a", "b").waitUntil().operationIsCompleted();

    // then
    assertThat(incidentReader.getAllIncidentsByProcessInstanceKey(processInstanceKey).get(0).getErrorMessage()).isEqualTo(errorMessageWithoutWhiteSpaces);
    ListViewResponseDto response = listViewReader.queryProcessInstances(TestUtil.createGetAllProcessInstancesRequest());
    ListViewProcessInstanceDto processInstances  = response.getProcessInstances().get(0);
    assertThat(processInstances).isNotNull();
    assertThat(processInstances.getOperations().get(0).getErrorMessage()).doesNotStartWith(" ").doesNotEndWith(" ");
  }

  // OPE-619
  @Test
  public void testFilterErrorMessagesBySubstring() throws Exception {
    // Given
    String errorMessageToFind =        "   Find me by query only a substring  ";
    String anotherErrorMessageToFind = "   Unexpected error while executing query 'all_users'";

    // when
    String processInstanceKey = setupIncidentWith(errorMessageToFind).toString();
    String anotherProcessInstanceKey = setupIncidentWith(anotherErrorMessageToFind).toString();

    // then ensure that ...

    // 1. case should not find any results
    assertSearchResults(searchForErrorMessages("no"), 0);
    // 2. case should find only one (first) result
    assertSearchResults(searchForErrorMessages("only"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("by query only a"), 1, processInstanceKey);
    // 3. case should find two one results , because 'query' is in both error messages
    assertSearchResults(searchForErrorMessages("query"), 2, processInstanceKey, anotherProcessInstanceKey);
    // 4. case (ignore lower/upper characters) should find one result because 'Find' is in only one errorMessage
    assertSearchResults(searchForErrorMessages("find"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("Find"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*Find*"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*find*"), 1, processInstanceKey);
    // 5. case use wildcard query when searchstring contains the wildcard character
    assertSearchResults(searchForErrorMessages("que"), 0, processInstanceKey);
    assertSearchResults(searchForErrorMessages("que*"), 2, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*user*"), 1, processInstanceKey);
  }

  protected void assertSearchResults(ListViewResponseDto results,int count,String ...processInstanceKeys) {
    assertThat(results.getTotalCount()).isEqualTo(count);
    results.getProcessInstances().stream().allMatch(
       processInstance -> Arrays.asList(processInstanceKeys).contains(processInstance.getId())
    );
  }

  protected ListViewResponseDto searchForErrorMessages(String errorMessage) {
    ListViewRequestDto queriesRequest = TestUtil.createGetAllProcessInstancesRequest();
    queriesRequest.getQuery().setErrorMessage(errorMessage);
    return listViewReader.queryProcessInstances(queriesRequest);
  }

  protected Long setupIncidentWith(String errorMessage) {
    return tester
        .deployProcess("demoProcess_v_1.bpmn").waitUntil().processIsDeployed()
        .startProcessInstance("demoProcess","{\"a\": \"b\"}").failTask("taskA", errorMessage)
        .waitUntil().processInstanceIsFinished()
        .getProcessInstanceKey();
  }

}
