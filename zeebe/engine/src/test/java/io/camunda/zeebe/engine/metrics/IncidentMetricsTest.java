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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class IncidentMetricsTest {

  private static final String PROCESS_ID = "process";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldUpdateIncidentMetricsIfIncidentOccurs() {
    // given - process with a call activity referencing a non-existing process
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("call", a -> a.zeebeProcessId("non-existing"))
                .endEvent()
                .done())
        .deploy();

    // when - create process instance that will cause an incident
    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then - wait for incident to be created and verify metrics
    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();
    assertThat(createdIncidentsMetric()).describedAs("Created incidents metric").isOne();
    assertThat(pendingIncidentsMetric()).describedAs("Pending incidents metric").isOne();
    assertThat(resolvedIncidentsMetric()).describedAs("Resolved incidents metric").isZero();
  }

  @Test
  public void shouldUpdateIncidentMetricsIfIncidentIsResolved() {
    // given - process with a call activity referencing a non-existing process
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .callActivity("call", a -> a.zeebeProcessId("non-existing"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.incidentRecords(IncidentIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when - cancel the process instance, resolving the incident
    engine.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then - wait for process to be canceled and verify metrics
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    assertThat(createdIncidentsMetric()).describedAs("Created incidents metric").isOne();
    assertThat(pendingIncidentsMetric()).describedAs("Pending incidents metric").isZero();
    assertThat(resolvedIncidentsMetric()).describedAs("Resolved incidents metric").isOne();
  }

  private Double createdIncidentsMetric() {
    return engine
        .getMeterRegistry()
        .get("zeebe.incident.events.total")
        .tag("action", "created")
        .counter()
        .count();
  }

  private Double pendingIncidentsMetric() {
    return engine.getMeterRegistry().get("zeebe.pending.incidents.total").gauge().value();
  }

  private Double resolvedIncidentsMetric() {
    return engine
        .getMeterRegistry()
        .get("zeebe.incident.events.total")
        .tag("action", "resolved")
        .counter()
        .count();
  }
}
