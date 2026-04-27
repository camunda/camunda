/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ProcessDefinitionMetricsTest {

  private static final String PROCESS_ID_A = "processA";
  private static final String PROCESS_ID_B = "processB";

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldTrackSingleDeployedProcessDefinition() {
    // when
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .deploy();

    // then
    assertThat(definitionsCountGauge()).describedAs("unique process definitions count").isOne();
    assertThat(versionsCountGauge()).describedAs("total deployed versions count").isOne();
  }

  @Test
  public void shouldIncrementVersionCountButNotDefinitionCountOnRedeployment() {
    // given
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .deploy();

    // when - redeploy a modified version of the same process ID
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID_A)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("work"))
                .endEvent()
                .done())
        .deploy();

    // then - same BPMN process ID, so unique definitions stays at 1, but total versions is 2
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count after redeployment")
        .isOne();
    assertThat(versionsCountGauge())
        .describedAs("total deployed versions count after redeployment")
        .isEqualTo(2);
  }

  @Test
  public void shouldTrackMultipleDistinctProcessDefinitions() {
    // when
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_B).startEvent().endEvent().done())
        .deploy();

    // then
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count for two distinct processes")
        .isEqualTo(2);
    assertThat(versionsCountGauge())
        .describedAs("total deployed versions count for two distinct processes")
        .isEqualTo(2);
  }

  @Test
  public void shouldTrackResourceSizeGauge() {
    // when
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .deploy();

    // then - size gauge is registered and reports a positive byte count
    assertThat(resourceSizeGauge(1))
        .describedAs("resource size gauge for %s v1", PROCESS_ID_A)
        .isPositive();
  }

  @Test
  public void shouldDecrementCountsAndRemoveSizeGaugeOnDeletion() {
    // given
    final long processDefinitionKey = deploySimpleProcess(PROCESS_ID_A);
    assertThat(definitionsCountGauge()).isOne();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withBpmnProcessId(PROCESS_ID_A)
        .await();

    // then
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count after deletion")
        .isZero();
    assertThat(versionsCountGauge())
        .describedAs("total deployed versions count after deletion")
        .isZero();
    assertThatCode(() -> resourceSizeGauge(1))
        .describedAs("size gauge for deleted process version should be removed from registry")
        .isInstanceOf(MeterNotFoundException.class);
  }

  @Test
  public void shouldDecrementVersionCountButNotDefinitionCountWhenOneVersionRemains() {
    // given - deploy two versions of the same process
    deploySimpleProcess(PROCESS_ID_A);
    final long v2Key =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(PROCESS_ID_A)
                    .startEvent()
                    .serviceTask("task", t -> t.zeebeJobType("work"))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0)
            .getProcessDefinitionKey();

    assertThat(versionsCountGauge()).isEqualTo(2);

    // when - delete only v2
    engine.resourceDeletion().withResourceKey(v2Key).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withBpmnProcessId(PROCESS_ID_A)
        .withVersion(2)
        .await();

    // then - definition still tracked because v1 remains; only version count decremented
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count should remain 1 since v1 still exists")
        .isOne();
    assertThat(versionsCountGauge())
        .describedAs("total deployed versions count after deleting v2")
        .isOne();
    assertThatCode(() -> resourceSizeGauge(2))
        .describedAs("size gauge for deleted v2 should be removed")
        .isInstanceOf(MeterNotFoundException.class);
    assertThat(resourceSizeGauge(1))
        .describedAs("size gauge for remaining v1 should still exist")
        .isPositive();
  }

  @Test
  public void shouldRecoverCountMetricsAfterBrokerRestart() {
    // given - deploy two distinct processes before restart
    deploySimpleProcess(PROCESS_ID_A);
    deploySimpleProcess(PROCESS_ID_B);

    assertThat(definitionsCountGauge()).isEqualTo(2);
    assertThat(versionsCountGauge()).isEqualTo(2);

    // when - snapshot and restart the engine
    engine.snapshot();
    engine.stop();
    engine.start();

    // then - counts are recovered from state without requiring any new deployments
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count should be recovered after restart")
        .isEqualTo(2);
    assertThat(versionsCountGauge())
        .describedAs("total deployed versions count should be recovered after restart")
        .isEqualTo(2);
  }

  @Test
  public void shouldRecoverPerVersionSizeGaugeAfterBrokerRestart() {
    // given
    deploySimpleProcess(PROCESS_ID_A);
    final double sizeBeforeRestart = resourceSizeGauge(1);
    assertThat(sizeBeforeRestart).isPositive();

    // when
    engine.snapshot();
    engine.stop();
    engine.start();

    // then - size gauge is re-registered from state with the same byte count
    assertThat(resourceSizeGauge(1))
        .describedAs("resource size gauge should be re-registered after restart with same value")
        .isEqualTo(sizeBeforeRestart);
  }

  private long deploySimpleProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }

  private double definitionsCountGauge() {
    return engine.getMeterRegistry().get("zeebe.process.definitions.count").gauge().value();
  }

  private double versionsCountGauge() {
    return engine.getMeterRegistry().get("zeebe.process.definition.versions.count").gauge().value();
  }

  private double resourceSizeGauge(final int version) {
    return engine
        .getMeterRegistry()
        .get("zeebe.process.definition.resource.size.bytes")
        .tag("bpmnProcessId", ProcessDefinitionMetricsTest.PROCESS_ID_A)
        .tag("version", String.valueOf(version))
        .gauge()
        .value();
  }
}
