/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ErrorMessagesZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired private ResolveIncidentHandler updateRetriesHandler;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Autowired private IncidentReader incidentReader;

  @Autowired private ListViewReader listViewReader;

  @Override
  @Before
  public void before() {
    super.before();
    injectCamundaClientIntoOperationHandler();
  }

  private void injectCamundaClientIntoOperationHandler() {
    cancelProcessInstanceHandler.setCamundaClient(camundaClient);
    updateRetriesHandler.setCamundaClient(camundaClient);
    updateVariableHandler.setCamundaClient(camundaClient);
  }

  // OPE-619
  @Test
  public void testFilterErrorMessagesBySubstring() throws Exception {
    // Given
    final String errorMessageToFind = "   Find me by query only a substring  ";
    final String anotherErrorMessageToFind =
        "   Unexpected error while executing query 'all_users'";

    // when
    final String processInstanceKey = setupIncidentWith(errorMessageToFind).toString();
    final String anotherProcessInstanceKey =
        setupIncidentWith(anotherErrorMessageToFind).toString();

    // then ensure that ...

    // 1. case should not find any results
    assertSearchResults(searchForErrorMessages("no"), 0);
    // 2. case should find only one (first) result
    assertSearchResults(searchForErrorMessages("only"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("by query only a"), 1, processInstanceKey);
    // 3. case should find two one results , because 'query' is in both error messages
    assertSearchResults(
        searchForErrorMessages("query"), 2, processInstanceKey, anotherProcessInstanceKey);
    // 4. case (ignore lower/upper characters) should find one result because 'Find' is in only one
    // errorMessage
    assertSearchResults(searchForErrorMessages("find"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("Find"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*Find*"), 1, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*find*"), 1, processInstanceKey);
    // 5. case use wildcard query when searchstring contains the wildcard character
    assertSearchResults(searchForErrorMessages("que"), 0, processInstanceKey);
    assertSearchResults(searchForErrorMessages("que*"), 2, processInstanceKey);
    assertSearchResults(searchForErrorMessages("*user*"), 1, processInstanceKey);
  }

  protected void assertSearchResults(
      final ListViewResponseDto results, final int count, final String... processInstanceKeys) {
    assertThat(results.getTotalCount()).isEqualTo(count);
    results.getProcessInstances().stream()
        .allMatch(
            processInstance ->
                Arrays.asList(processInstanceKeys).contains(processInstance.getId()));
  }

  protected ListViewResponseDto searchForErrorMessages(final String errorMessage) {
    final ListViewRequestDto queriesRequest = createGetAllProcessInstancesRequest();
    queriesRequest.getQuery().setErrorMessage(errorMessage);
    return listViewReader.queryProcessInstances(queriesRequest);
  }

  protected Long setupIncidentWith(final String errorMessage) {
    return tester
        .deployProcess("demoProcess_v_1.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .failTask("taskA", errorMessage)
        .waitUntil()
        .incidentIsActive()
        .getProcessInstanceKey();
  }
}
