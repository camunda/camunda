/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.metric;

import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class MetricIT extends OperateZeebeIntegrationTest {
  
  @Autowired
  private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired
  private ResolveIncidentHandler updateRetriesHandler;

  @Autowired
  private UpdateVariableHandler updateVariableHandler;

  @Before
  public void before() {
    super.before();
    injectZeebeClientIntoOperationHandler();
    clearMetrics();
  }

  private void injectZeebeClientIntoOperationHandler() {
    cancelProcessInstanceHandler.setZeebeClient(zeebeClient);
    updateRetriesHandler.setZeebeClient(zeebeClient);
    updateVariableHandler.setZeebeClient(zeebeClient);
  }

  @Test // OPE-624 
  public void testProcessedEventsDuringImport() {
    // Given metrics are enabled
    // When
    tester
      .deployProcess("demoProcess_v_1.bpmn").waitUntil().processIsDeployed()
      .startProcessInstance("demoProcess","{\"a\": \"b\"}")
        .waitUntil().processInstanceIsStarted();
    // Then
    assertThatMetricsFrom(mockMvc,allOf(
        containsString("operate_events_processed_total"),
        containsString("operate_import_query"),
        containsString("operate_import_index_query")
    ));
  }
  
  @Test // OPE-624 
  public void testProcessedEventsDuringImportWithIncidents() {
    // Given metrics are enabled
    // When
    tester
      .deployProcess("demoProcess_v_1.bpmn").waitUntil().processIsDeployed()
      .startProcessInstance("demoProcess","{\"a\": \"b\"}")
      .and()
      .failTask("taskA","Some error").waitUntil().incidentIsActive();
    // Then
    assertThatMetricsFrom(mockMvc, containsString("operate_events_processed_total"));
  }
  
  @Test // OPE-642
  public void testOperationThatSucceeded() throws Exception {
    // Given metrics are enabled
    // When
    tester
      .deployProcess("demoProcess_v_2.bpmn").waitUntil().processIsDeployed()
      .and()
      .startProcessInstance("demoProcess").waitUntil().processInstanceIsStarted()
      .and()
      .updateVariableOperation("a","\"newValue\"").waitUntil().operationIsCompleted();
    // Then
    assertThatMetricsFrom(mockMvc,
        new MetricAssert.ValueMatcher("operate_commands_total{status=\"" + OperationState.SENT + "\",type=\"" + OperationType.UPDATE_VARIABLE + "\",}",
            d -> d.doubleValue() == 1));
  }
  
  @Test // OPE-642
  public void testOperationThatFailed() throws Exception {
    // given
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
      Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent()
        .endEvent()
        .done();
    
    tester
      .deployProcess(startEndProcess, "startEndProcess.bpmn").processIsDeployed()
      .and()
      .startProcessInstance(bpmnProcessId).waitUntil().processInstanceIsCompleted()
      .and()
      .cancelProcessInstanceOperation().waitUntil().operationIsCompleted();
    // Then
    assertThatMetricsFrom(mockMvc,
        new MetricAssert.ValueMatcher("operate_commands_total{status=\""+OperationState.FAILED+"\",type=\""+OperationType.CANCEL_PROCESS_INSTANCE+"\",}",
            d -> d.doubleValue() == 1));
  }
  
}
