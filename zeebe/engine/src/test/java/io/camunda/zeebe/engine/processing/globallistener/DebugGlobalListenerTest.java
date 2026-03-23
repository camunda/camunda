/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class DebugGlobalListenerTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  /**
   * With a global EL registered: create instance, complete the EL job manually, dump all records to
   * diagnose whether the COMPLETE command is processed by the engine.
   */
  @Test
  public void dumpAllRecordsAfterCompleteAttempt() {
    // Register global execution listener for serviceTask start
    engine
        .globalListener()
        .withId("test-el")
        .withType("gel-type")
        .withListenerType(GlobalListenerType.EXECUTION)
        .withEventTypes("start")
        .withElementTypes("serviceTask")
        .create();

    // Deploy process
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("main-job"))
                .endEvent()
                .done())
        .deploy();

    // Create process instance
    final long piKey = engine.processInstance().ofBpmnProcessId("p").create();

    // Wait for the EL JOB to be CREATED
    final Record<JobRecordValue> elJobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(piKey)
            .withType("gel-type")
            .getFirst();

    assertThat(elJobCreated).isNotNull();
    final long elJobKey = elJobCreated.getKey();

    // Try to complete the EL job — use a short timeout so we don't wait 5 seconds
    final long prevMaxWait = 500;
    RecordingExporter.setMaximumWaitTime(prevMaxWait);
    try {
      engine.job().ofInstance(piKey).withType("gel-type").complete();
    } catch (final Exception e) {
      // Expected timeout — we want to see what records were produced
    } finally {
      RecordingExporter.setMaximumWaitTime(5000); // restore default
    }

    // Give the engine a moment to process anything it can
    try {
      Thread.sleep(1000);
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    // Dump ALL records (non-blocking snapshot)
    final String allRecords =
        RecordingExporter.getRecords().stream()
            .filter(
                r -> {
                  final String valueType = r.getValueType().name();
                  return valueType.equals("JOB")
                      || valueType.equals("INCIDENT")
                      || valueType.equals("PROCESS_INSTANCE")
                      || valueType.equals("GLOBAL_LISTENER")
                      || valueType.equals("GLOBAL_LISTENER_BATCH");
                })
            .map(
                r ->
                    String.format(
                        "[%s] %s %s %s | key=%d | %s",
                        r.getPosition(),
                        r.getRecordType(),
                        r.getValueType(),
                        r.getIntent(),
                        r.getKey(),
                        r.getValue()))
            .collect(Collectors.joining("\n"));

    // Force failure to see the dump
    org.junit.Assert.fail(
        "=== RECORD DUMP for PI "
            + piKey
            + " (EL job key="
            + elJobKey
            + ") ===\n"
            + allRecords
            + "\n=== END DUMP ===");
  }

  /** Reference test: BPMN-level EL works correctly with the same complete() pattern. */
  @Test
  public void bpmnLevelElJobCompletes() {
    // Deploy process WITH a BPMN-level start execution listener on the service task
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("p")
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("main-job")
                            .zeebeStartExecutionListener("bpmn-el-type"))
                .endEvent()
                .done())
        .deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId("p").create();

    // Complete the BPMN-level EL job — this should work
    engine.job().ofInstance(piKey).withType("bpmn-el-type").complete();

    // Complete the service task
    engine.job().ofInstance(piKey).withType("main-job").complete();

    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(piKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }
}
