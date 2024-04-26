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
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.qa.util.RestAPITestUtil.createGetAllProcessInstancesRequest;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
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
    final String errorMessageWithWhitespaces = "   Error message with white spaces   ";
    final String errorMessageWithoutWhiteSpaces = "Error message with white spaces";

    // when
    final Long processInstanceKey = setupIncidentWith(errorMessageWithWhitespaces);
    tester.updateVariableOperation("a", "wrong value").waitUntil().operationIsFailed();

    // then
    assertThat(
            incidentReader
                .getAllIncidentsByProcessInstanceKey(processInstanceKey)
                .get(0)
                .getErrorMessage())
        .isEqualTo(errorMessageWithoutWhiteSpaces);
    final ListViewResponseDto response =
        listViewReader.queryProcessInstances(createGetAllProcessInstancesRequest());
    final ListViewProcessInstanceDto processInstances = response.getProcessInstances().get(0);
    assertThat(processInstances).isNotNull();
    assertThat(processInstances.getOperations().get(0).getErrorMessage())
        .doesNotStartWith(" ")
        .doesNotEndWith(" ");
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
