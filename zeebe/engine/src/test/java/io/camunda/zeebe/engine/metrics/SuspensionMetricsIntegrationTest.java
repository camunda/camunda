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
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class SuspensionMetricsIntegrationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRecordSuspendAndResumeMetrics() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when — suspend
    engine.processInstance().withInstanceKey(piKey).suspend();

    // then
    assertThat(suspensionCounter("suspended")).isOne();

    // when — resume
    engine.processInstance().withInstanceKey(piKey).resume();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.RESUMED)
        .withProcessInstanceKey(piKey)
        .await();

    // then
    assertThat(suspensionCounter("resumed")).isOne();
  }

  @Test
  public void shouldRecordJobSuspensionMetrics() {
    // given — a process with a service task that has an active job
    final String processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED).withProcessInstanceKey(piKey).await();

    // when — suspend (parks the job)
    engine.processInstance().withInstanceKey(piKey).suspend();

    // then
    RecordingExporter.jobRecords(JobIntent.SUSPENDED).withProcessInstanceKey(piKey).await();
    assertThat(jobSuspensionCounter("suspended"))
        .describedAs("job suspended count")
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  public void shouldRecordCommandBufferingMetrics() {
    // given — a process waiting on a message catch event (has an open message subscription)
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(piKey)
        .await();

    // when — suspend tears down the message subscription; the DELETE ack buffers a REOPEN
    engine.processInstance().withInstanceKey(piKey).suspend();
    RecordingExporter.records().withIntent(BufferedCommandIntent.BUFFERED).await();

    // then
    assertThat(bufferedCommandCounter("buffered"))
        .describedAs("buffered command count")
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  public void shouldRecordDroppedCommandsOnTerminationWhileSuspended() {
    // given — a suspended instance with a buffered REOPEN command
    final String processId = Strings.newRandomValidBpmnId();
    final String messageName = Strings.newRandomValidBpmnId();
    final String correlationKey = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(
                    "msg",
                    e ->
                        e.message(
                            m ->
                                m.name(messageName)
                                    .zeebeCorrelationKey("=\"%s\"".formatted(correlationKey))))
                .endEvent()
                .done())
        .deploy();
    final long piKey = engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.processMessageSubscriptionRecords(ProcessMessageSubscriptionIntent.CREATED)
        .withProcessInstanceKey(piKey)
        .await();
    engine.processInstance().withInstanceKey(piKey).suspend();
    RecordingExporter.records().withIntent(BufferedCommandIntent.BUFFERED).await();

    // when — cancel while suspended, dropping the buffered command
    engine.processInstance().withInstanceKey(piKey).cancel();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
        .withProcessInstanceKey(piKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();

    // then
    assertThat(bufferedCommandCounter("dropped"))
        .describedAs("dropped command count")
        .isGreaterThanOrEqualTo(1);
  }

  private MeterRegistry registry() {
    return engine.getMeterRegistry();
  }

  private double suspensionCounter(final String action) {
    return registry()
        .get("zeebe.process.instance.suspension.events.total")
        .tag("action", action)
        .counter()
        .count();
  }

  private double jobSuspensionCounter(final String action) {
    return registry()
        .get("zeebe.job.suspension.events.total")
        .tag("action", action)
        .counter()
        .count();
  }

  private double bufferedCommandCounter(final String action) {
    return registry()
        .get("zeebe.buffered.commands.events.total")
        .tag("action", action)
        .counter()
        .count();
  }
}
