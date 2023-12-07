/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.operate.entities.ErrorType;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.BatchRequest;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.OperationsManager;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentResponseDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static io.camunda.operate.entities.ErrorType.JOB_NO_RETRIES;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        //configure webhook to notify about the incidents
        OperateProperties.PREFIX + ".alert.webhook = http://somepath",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"})
public class IncidentWithFailingOperationIT extends OperateZeebeAbstractIT {

  private static final Logger logger = LoggerFactory.getLogger(IncidentWithFailingOperationIT.class);

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @MockBean
  private IncidentNotifier incidentNotifier;

  @SpyBean
  private OperationsManager operationsManager;

  @Before
  public void before() {
    super.before();
    updateRetriesHandler.setZeebeClient(super.getClient());
  }

  @Test
  public void testIncidentsAreReturned() throws Exception {
    AtomicInteger count = new AtomicInteger(0);
    doAnswer(invocation -> {
      if (count.get() < 4 ) {
        BatchRequest batchRequest = invocation.getArgument(4);
        batchRequest.update("wrong_index", "someId", Map.of());
        count.incrementAndGet();
      } else {
        invocation.callRealMethod();
        count.incrementAndGet();
      }
      return null;
    }).when(operationsManager)
        .completeOperation(any(), any(), any(), any(),
            any());

    // having
    String processId = "complexProcess";
    deployProcess("complexProcess_v_3.bpmn");
    final long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"count\":3}");
    final String errorMsg = "some error";
    final String activityId = "alwaysFailingTask";
    ZeebeTestUtil.failTask(zeebeClient, activityId, getWorkerName(), 3, errorMsg);
    searchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, processInstanceKey, 4);

    postOperationWithOKResponse(processInstanceKey, new CreateOperationRequestDto(OperationType.RESOLVE_INCIDENT));
    executeOneBatch();

    //this test will fail import for incidents
    //we need to wait at least 3 X 2sec time to cover 3 backoff of importer
    Thread.sleep(8000L);

    searchTestRule.processAllRecordsAndWait(incidentsAreActiveCheck, processInstanceKey, 3);

    //when
    MvcResult mvcResult = getRequest(getIncidentsURL(processInstanceKey));
    final IncidentResponseDto incidentResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {
    });

    //then
    //one of the incidents won't be recreated, but the other 3 will be
    assertThat(incidentResponse).isNotNull();
    assertThat(incidentResponse.getCount()).isEqualTo(3);
    assertThat(incidentResponse.getIncidents()).hasSize(3);
    logger.info("Incidents found: " + incidentResponse.getIncidents().toString());

    assertIncident(incidentResponse, "Assertion failure on evaluate the expression '{taskOrderId:assert(orderId, orderId!=null, \"no variable found for name 'orderId'\")}': no variable found for name 'orderId'", "upperTask", ErrorType.IO_MAPPING_ERROR);
    assertIncident(incidentResponse, "Failed to extract the correlation key for 'clientId': The value must be either a string or a number, but was 'NULL'.", "messageCatchEvent", ErrorType.EXTRACT_VALUE_ERROR);
    assertIncident(incidentResponse, "Expected at least one condition to evaluate to true, or to have a default flow", "exclusiveGateway", ErrorType.CONDITION_ERROR);

    verify(operationsManager, atLeast(4)).completeOperation(any(), any(), any(), any(), any());
  }

  private void assertIncident(IncidentResponseDto incidentResponse, String errorMsg, String activityId, ErrorType errorType) {
    final Optional<IncidentDto> incidentOpt = incidentResponse.getIncidents().stream()
        .filter(inc -> inc.getErrorType().getName().equals(errorType.getTitle())).findFirst();
    assertThat(incidentOpt).isPresent();
    final IncidentDto inc = incidentOpt.get();
    assertThat(inc.getId()).as(activityId + ".id").isNotNull();
    assertThat(inc.getCreationTime()).as(activityId + ".creationTime").isNotNull();
    assertThat(inc.getErrorMessage()).as(activityId + ".errorMessage").startsWith(errorMsg);
    assertThat(inc.getFlowNodeId()).as(activityId + ".flowNodeId").isEqualTo(activityId);
    assertThat(inc.getFlowNodeInstanceId()).as(activityId + ".flowNodeInstanceId").isNotNull();
    if (errorType.equals(JOB_NO_RETRIES)) {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNotNull();
    } else {
      assertThat(inc.getJobId()).as(activityId + ".jobKey").isNull();
    }
  }

  protected String getIncidentsURL(long processInstanceKey) {
    return String.format(PROCESS_INSTANCE_URL + "/%s/incidents", processInstanceKey);
  }

}
