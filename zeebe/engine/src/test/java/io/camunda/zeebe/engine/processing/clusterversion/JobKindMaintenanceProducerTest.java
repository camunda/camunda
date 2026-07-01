/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.clusterversion.ClusterVersionRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterVersionIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

/**
 * Engine-level end-to-end test that gives the {@link Capability#JOB_KIND_MAINTENANCE} gate real
 * teeth. The catalog entry by itself (committed earlier) was scaffolding — no producer wrote the
 * new value, no consumer cared about it. This commit added both:
 *
 * <ul>
 *   <li><b>Producer</b> in {@code BpmnJobBehavior.createNewJob}: when a service task carries a task
 *       header {@link BpmnJobBehavior#MAINTENANCE_TASK_HEADER} <em>and</em> the gate is active, the
 *       JobRecord is stamped with {@link JobKind#MAINTENANCE} instead of {@link
 *       JobKind#BPMN_ELEMENT}. Below the gate the header is silently ignored and the legacy value
 *       is stamped.
 *   <li><b>Consumer</b> in {@code JobBatchCollector.collectJobs}: jobs whose kind is {@code
 *       MAINTENANCE} are skipped by the {@code ActivateJobs} flow, so external workers cannot claim
 *       them.
 * </ul>
 *
 * The hazard the gate now actively guards: a new-binary leader emits {@code
 * JobRecord{kind=MAINTENANCE}} as soon as any deployer adds the header. A pre-feature follower
 * would crash on {@code Enum.valueOf("MAINTENANCE")} on replay. The gate holds the producer back
 * until the operator raises ECV — at which point all replicas already know the value.
 */
public final class JobKindMaintenanceProducerTest {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldStampBpmnElementKindBelowGate() {
    // given — fresh engine at BASELINE; deployer marks the service task with the maintenance header
    deployProcessWithMaintenanceMarker();

    // when
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — the job carries BPMN_ELEMENT, NOT MAINTENANCE, even though the header is set
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .withType(JOB_TYPE)
            .getFirst();
    assertThat(job.getValue().getJobKind())
        .as("Below the gate the producer ignores the maintenance header")
        .isEqualTo(JobKind.BPMN_ELEMENT);
  }

  @Test
  public void shouldStampMaintenanceKindAboveGate() {
    // given — raise ECV; the producer's gate check now passes
    raiseToMaintenanceOrdinal();
    deployProcessWithMaintenanceMarker();

    // when
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — the maintenance header now promotes the kind
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .withType(JOB_TYPE)
            .getFirst();
    assertThat(job.getValue().getJobKind())
        .as("Above the gate the producer respects the maintenance header")
        .isEqualTo(JobKind.MAINTENANCE);
  }

  @Test
  public void shouldNotPromoteWithoutTheHeaderEvenAboveGate() {
    // given — gate is open, but the deployment carries no maintenance header
    raiseToMaintenanceOrdinal();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then — without the header, the kind stays at the legacy default
    final var job =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(pi)
            .withType(JOB_TYPE)
            .getFirst();
    assertThat(job.getValue().getJobKind()).isEqualTo(JobKind.BPMN_ELEMENT);
  }

  @Test
  public void shouldSkipMaintenanceJobsInActivateBatch() {
    // given — raise, deploy with header → job is created as MAINTENANCE
    raiseToMaintenanceOrdinal();
    deployProcessWithMaintenanceMarker();
    final long pi = engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(pi)
        .withType(JOB_TYPE)
        .getFirst();

    // when — an external worker tries to activate jobs of this type
    final var batch = engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(10).activate();

    // then — the MAINTENANCE job is filtered out by the consumer; the batch is empty
    assertThat(batch.getValue().getJobKeys())
        .as("MAINTENANCE jobs are engine-internal; the consumer skips them in ActivateJobs")
        .isEmpty();
  }

  private void deployProcessWithMaintenanceMarker() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(JOB_TYPE)
                            .zeebeTaskHeader(BpmnJobBehavior.MAINTENANCE_TASK_HEADER, "true"))
                .endEvent()
                .done())
        .deploy();
  }

  private void raiseToMaintenanceOrdinal() {
    final int target = Capability.JOB_KIND_MAINTENANCE.at();
    engine.writeRecords(
        RecordToWrite.command()
            .clusterVersion(
                ClusterVersionIntent.RAISE,
                new ClusterVersionRecord().setLine(810).setOrdinal(target)));
    RecordingExporter.records()
        .filter(r -> r.getValueType() == ValueType.CLUSTER_VERSION)
        .filter(r -> r.getIntent() == ClusterVersionIntent.APPLIED)
        .filter(r -> ((ClusterVersionRecord) r.getValue()).getOrdinal() == target)
        .getFirst();
  }
}
