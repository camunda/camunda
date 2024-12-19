/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.metric;

import static io.camunda.operate.util.MetricAssert.assertThatMetricsFrom;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import io.camunda.operate.Metrics;
import io.camunda.operate.management.ModelMetricProvider;
import io.camunda.operate.metric.ImporterMetricsZeebeImportIT.ManagementPropertyRemoval;
import io.camunda.operate.util.MetricAssert;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.webapp.zeebe.operation.CancelProcessInstanceHandler;
import io.camunda.operate.webapp.zeebe.operation.ResolveIncidentHandler;
import io.camunda.operate.webapp.zeebe.operation.UpdateVariableHandler;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ManagementPropertyRemoval.class)
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
    injectCamundaClientIntoOperationHandler();
    clearMetrics();
  }

  private void injectCamundaClientIntoOperationHandler() {
    cancelProcessInstanceHandler.setCamundaClient(camundaClient);
    updateRetriesHandler.setCamundaClient(camundaClient);
    updateVariableHandler.setCamundaClient(camundaClient);
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

  public static class ManagementPropertyRemoval
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext ctx) {
      final var modifiedPropertySource = new HashMap<String, Object>();

      final var environment = ctx.getEnvironment();
      final var propertySources = environment.getPropertySources();
      final var iterator = propertySources.iterator();
      PropertySource<?> applicationPropertySource = null;

      // get the "application.properties" containing
      // the port and address configurations
      while (iterator.hasNext()) {
        final var propertySource = iterator.next();
        final var propertySourceName = propertySource.getName();

        // the "application.properties" is loaded as the last one
        // if present.
        if (propertySourceName.contains("application.properties")) {
          applicationPropertySource = propertySource;
          break;
        }
      }

      if (applicationPropertySource != null
          && applicationPropertySource instanceof final MapPropertySource mapPropertySource) {
        final var propertyNames = mapPropertySource.getPropertyNames();
        final var applicationPropertySourceName = applicationPropertySource.getName();

        // copy all all properties
        for (final String propName : propertyNames) {
          final var propValue = (String) mapPropertySource.getProperty(propName);
          modifiedPropertySource.put(propName, propValue);
        }

        // remove all port and address configurations
        // that way, mock mvc will load all controllers
        // including the actuator web endpoints
        modifiedPropertySource.remove("server.port");
        modifiedPropertySource.remove("server.address");
        modifiedPropertySource.remove("management.server.port");
        modifiedPropertySource.remove("management.server.address");

        // replace existing "application.properties" with the new properties
        // that do not contain the port and address configurations
        final var newPropertySource =
            new MapPropertySource(applicationPropertySourceName, modifiedPropertySource);
        propertySources.remove(applicationPropertySourceName);
        propertySources.addLast(newPropertySource);
      }
    }
  }
}
