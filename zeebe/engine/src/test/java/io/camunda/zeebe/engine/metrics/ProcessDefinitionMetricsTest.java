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
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessDefinitionMetricsTest {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionMetricsTest.class);
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
  }

  @Test
  public void shouldNotIncrementDefinitionCountOnRedeployment() {
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

    // then - same BPMN process ID, so unique definitions stays at 1
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count after redeployment")
        .isOne();
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
  }

  @Test
  public void shouldTrackResourceSizeGauge() {
    // when
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .deploy();

    // then - size gauge is registered and reports a positive byte count
    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("resource size gauge for %s", PROCESS_ID_A)
        .isPositive();
  }

  @Test
  public void shouldReflectResourceBytesInSizeGauge() {
    // given - a BPMN with a documentation field of a known size
    final int paddingBytes = 1024;
    final String padding = "x".repeat(paddingBytes);

    // when
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID_A)
                .startEvent()
                .endEvent()
                .documentation(padding)
                .done())
        .deploy();

    // then - size gauge reports more bytes than the injected payload
    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("resource size gauge should reflect the deployed BPMN bytes")
        .isGreaterThan(paddingBytes);
  }

  @Test
  public void shouldSumSizeAcrossVersionsOfSameProcess() {
    // given - a first version
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(PROCESS_ID_A).startEvent().endEvent().done())
        .deploy();
    final double sizeAfterV1 = resourceSizeGauge(PROCESS_ID_A);

    // when - deploy a second version of the same process
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID_A)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("work"))
                .endEvent()
                .done())
        .deploy();

    // then - the gauge reports the sum of both versions' sizes
    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("size gauge should aggregate bytes across all versions of the same process")
        .isGreaterThan(sizeAfterV1);
  }

  @Test
  public void shouldDecrementCountAndRemoveSizeGaugeOnDeletion() {
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
    assertThatCode(() -> resourceSizeGauge(PROCESS_ID_A))
        .describedAs("size gauge for deleted process should be removed from registry")
        .isInstanceOf(MeterNotFoundException.class);
  }

  @Test
  public void shouldSubtractDeletedVersionFromTotalSizeWhenOneVersionRemains() {
    // given - deploy two versions of the same process
    deploySimpleProcess(PROCESS_ID_A);
    final double sizeAfterV1 = resourceSizeGauge(PROCESS_ID_A);
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

    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("size after both versions deployed")
        .isGreaterThan(sizeAfterV1);

    // when - delete only v2
    engine.resourceDeletion().withResourceKey(v2Key).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DELETED)
        .withBpmnProcessId(PROCESS_ID_A)
        .withVersion(2)
        .await();

    // then - definition still tracked because v1 remains; size gauge falls back to v1's size
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count should remain 1 since v1 still exists")
        .isOne();
    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("size gauge should be reduced to v1's size only after v2 is deleted")
        .isEqualTo(sizeAfterV1);
  }

  @Test
  public void shouldRecoverDefinitionCountAfterBrokerRestart() {
    // given - deploy two distinct processes before restart
    deploySimpleProcess(PROCESS_ID_A);
    deploySimpleProcess(PROCESS_ID_B);

    assertThat(definitionsCountGauge()).isEqualTo(2);

    // when - snapshot and restart the engine
    engine.snapshot();
    engine.stop();
    engine.start();

    // then - count is recovered from state without requiring any new deployments
    assertThat(definitionsCountGauge())
        .describedAs("unique process definitions count should be recovered after restart")
        .isEqualTo(2);
  }

  @Test
  public void shouldRecoverPerProcessSizeGaugeAfterBrokerRestart() {
    // given
    deploySimpleProcess(PROCESS_ID_A);
    final double sizeBeforeRestart = resourceSizeGauge(PROCESS_ID_A);
    assertThat(sizeBeforeRestart).isPositive();

    // when
    engine.snapshot();
    engine.stop();
    engine.start();

    // then - size gauge is re-registered from state with the same byte count
    assertThat(resourceSizeGauge(PROCESS_ID_A))
        .describedAs("resource size gauge should be re-registered after restart with same value")
        .isEqualTo(sizeBeforeRestart);
  }

  @Test
  public void shouldInitializeMetricsForManyDeployedProcesses() throws IOException {
    // given - many distinct process definitions derived from a real BPMN file,
    // deployed in batched deployments to keep test setup fast.
    final byte[] baseBpmn;
    try (var in =
        getClass().getResourceAsStream("/processes/bankCustomerComplaintDisputeHandling.bpmn")) {
      baseBpmn = in.readAllBytes();
    }
    final String baseXml = new String(baseBpmn, StandardCharsets.UTF_8);
    final String originalProcessId = "bankDisputeHandling";

    final int processCount = 1000;
    final int batchSize = 10;
    for (int batch = 0; batch < processCount; batch += batchSize) {
      DeploymentClient deployment = engine.deployment();
      for (int i = batch; i < batch + batchSize; i++) {
        final String processId = "process-" + i;
        final byte[] resource =
            baseXml.replace(originalProcessId, processId).getBytes(StandardCharsets.UTF_8);
        deployment = deployment.withXmlResource(resource, processId + ".bpmn");
      }
      deployment.deploy();
    }
    assertThat(definitionsCountGauge())
        .describedAs("definitions count before restart")
        .isEqualTo(processCount);

    // when - snapshot and restart triggers the metric initialization scan
    engine.snapshot();
    engine.stop();
    final long startNanos = System.nanoTime();
    engine.start();
    final Duration restartDuration = Duration.ofNanos(System.nanoTime() - startNanos);

    LOG.info(
        "Engine restart with {} deployed process definitions (~{} bytes each) took {}",
        processCount,
        baseBpmn.length,
        restartDuration);

    // then - all definitions are recovered into the gauge
    assertThat(definitionsCountGauge())
        .describedAs("definitions count after restart with %d processes", processCount)
        .isEqualTo(processCount);
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

  private double resourceSizeGauge(final String bpmnProcessId) {
    return engine
        .getMeterRegistry()
        .get("zeebe.process.definition.resource.size.bytes")
        .tag("bpmnProcessId", bpmnProcessId)
        .gauge()
        .value();
  }
}
