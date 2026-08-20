/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class TenantMetricsTest {

  private static final String PROCESS_ID = "process";
  private static final String TENANT_A = "tenant-a";
  private static final String TENANT_B = "tenant-b";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCountDistinctTenants() {
    engine.deployment().withXmlResource(simpleProcess()).withTenantId(TENANT_A).deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_A).create();

    engine.deployment().withXmlResource(simpleProcess()).withTenantId(TENANT_B).deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_B).create();

    engine.usageMetrics().export();

    assertThat(activeTenantGaugeValue()).isEqualTo(2.0);
  }

  @Test
  public void shouldNotDoubleCountTenantSeenInMultipleIntervals() {
    engine.deployment().withXmlResource(simpleProcess()).withTenantId(TENANT_A).deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_A).create();
    engine.usageMetrics().export();

    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_A).create();
    engine.usageMetrics().export();

    assertThat(activeTenantGaugeValue()).isEqualTo(1.0);
  }

  @Test
  public void shouldAccumulateNewTenantsAcrossExportIntervals() {
    engine.deployment().withXmlResource(simpleProcess()).withTenantId(TENANT_A).deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_A).create();
    engine.usageMetrics().export();
    assertThat(activeTenantGaugeValue()).isEqualTo(1.0);

    engine.deployment().withXmlResource(simpleProcess()).withTenantId(TENANT_B).deploy();
    engine.processInstance().ofBpmnProcessId(PROCESS_ID).withTenantId(TENANT_B).create();
    engine.usageMetrics().export();

    assertThat(activeTenantGaugeValue()).isEqualTo(2.0);
  }

  private double activeTenantGaugeValue() {
    return engine.getMeterRegistry().get("zeebe.active.tenants.count").gauge().value();
  }

  private static BpmnModelInstance simpleProcess() {
    return Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();
  }
}
