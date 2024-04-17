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
package io.camunda.operate.metric;

import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import io.camunda.operate.Metrics;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.management.ModelMetricProvider;
import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ImporterMetricsZeebeImportIT extends OperateZeebeAbstractIT {

  @Autowired private CancelProcessInstanceHandler cancelProcessInstanceHandler;

  @Autowired private ResolveIncidentHandler updateRetriesHandler;

  @Autowired private UpdateVariableHandler updateVariableHandler;

  @Autowired private Metrics metrics;

  @Autowired private ModelMetricProvider modelMetricProvider;

  @Override
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
        .deployProcess("demoProcess_v_1.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .waitUntil()
        .processInstanceIsStarted();
    // Then
    assertThatMetricsFrom(
        mockMvc,
        allOf(
            containsString("operate_events_processed_total"),
            containsString("operate_import_query"),
            containsString("operate_import_index_query")));
  }

  @Test // OPE-624
  public void testProcessedEventsDuringImportWithIncidents() {
    // Given metrics are enabled
    // When
    tester
        .deployProcess("demoProcess_v_1.bpmn")
        .waitUntil()
        .processIsDeployed()
        .startProcessInstance("demoProcess", "{\"a\": \"b\"}")
        .and()
        .failTask("taskA", "Some error")
        .waitUntil()
        .incidentIsActive();
    // Then
    assertThatMetricsFrom(mockMvc, containsString("operate_events_processed_total"));
  }

  @Test // OPE-642
  public void testOperationThatSucceeded() throws Exception {
    // Given metrics are enabled
    // When
    tester
        .deployProcess("demoProcess_v_2.bpmn")
        .waitUntil()
        .processIsDeployed()
        .and()
        .startProcessInstance("demoProcess")
        .waitUntil()
        .processInstanceIsStarted()
        .and()
        .updateVariableOperation("a", "\"newValue\"")
        .waitUntil()
        .operationIsCompleted();
    // Then
    assertThatMetricsFrom(
        mockMvc,
        new MetricAssert.ValueMatcher(
            "operate_commands_total{status=\""
                + OperationState.SENT
                + "\",type=\""
                + OperationType.UPDATE_VARIABLE
                + "\",}",
            d -> d.doubleValue() == 1));
  }

  @Test // OPE-642
  public void testOperationThatFailed() throws Exception {
    // given
    final String bpmnProcessId = "startEndProcess";
    final BpmnModelInstance startEndProcess =
        Bpmn.createExecutableProcess(bpmnProcessId).startEvent().endEvent().done();

    tester
        .deployProcess(startEndProcess, "startEndProcess.bpmn")
        .processIsDeployed()
        .and()
        .startProcessInstance(bpmnProcessId)
        .waitUntil()
        .processInstanceIsCompleted()
        .and()
        .cancelProcessInstanceOperation()
        .waitUntil()
        .operationIsFailed();
    // Then
    assertThatMetricsFrom(
        mockMvc,
        new MetricAssert.ValueMatcher(
            "operate_commands_total{status=\""
                + OperationState.FAILED
                + "\",type=\""
                + OperationType.CANCEL_PROCESS_INSTANCE
                + "\",}",
            d -> d.doubleValue() == 1));
  }

  @Test // OPE-3857
  public void testBPMNAndDNMModelMetrics() {
    // Given metrics are registered
    metrics.registerGaugeSupplier(
        Metrics.GAUGE_BPMN_MODEL_COUNT,
        modelMetricProvider::getBPMNModelCount,
        Metrics.TAG_KEY_ORGANIZATIONID,
        "orga");
    metrics.registerGaugeSupplier(
        Metrics.GAUGE_DMN_MODEL_COUNT,
        modelMetricProvider::getDMNModelCount,
        Metrics.TAG_KEY_ORGANIZATIONID,
        "orga");
    // When
    tester.deployProcess("demoProcess_v_1.bpmn").waitUntil().processIsDeployed();
    tester.deployProcess("complexProcess_v_3.bpmn").waitUntil().processIsDeployed();
    //
    tester.deployDecision("invoiceBusinessDecisions_v_1.dmn").waitUntil().decisionsAreDeployed(2);

    // Then
    assertThatMetricsFrom(
        mockMvc,
        allOf(
            containsString(
                Metrics.GAUGE_BPMN_MODEL_COUNT.replace('.', '_')
                    + "{"
                    + Metrics.TAG_KEY_ORGANIZATIONID
                    + "=\"orga\",} 2.0"),
            containsString(
                Metrics.GAUGE_DMN_MODEL_COUNT.replace('.', '_')
                    + "{"
                    + Metrics.TAG_KEY_ORGANIZATIONID
                    + "=\"orga\",} 2.0")));
  }
}
