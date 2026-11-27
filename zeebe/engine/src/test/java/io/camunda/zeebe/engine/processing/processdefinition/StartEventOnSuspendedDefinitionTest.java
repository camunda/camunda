/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.Rule;
import org.junit.Test;

/**
 * Signal and conditional start events must not create an instance of a suspended process
 * definition, while the other subscribers of the same signal or condition still start theirs and
 * the broadcast or evaluation itself succeeds.
 */
public final class StartEventOnSuspendedDefinitionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotStartInstanceOfSuspendedDefinitionOnSignalButStartOthers() {
    // given - two definitions with a start event on the same signal, one of them suspended
    final var signalName = helper.getMessageName();
    final var suspendedId = helper.getBpmnProcessId() + "-suspended";
    final var activeId = helper.getBpmnProcessId() + "-active";
    final var suspendedKey = suspend(deploy(suspendedId, start -> start.signal(signalName)));
    deploy(activeId, start -> start.signal(signalName));

    // when
    final var broadcast = engine.signal().withSignalName(signalName).broadcast();
    final long markerKey = completeMarkerInstance();

    // then
    assertThat(broadcast.getValue().getSignalName()).isEqualTo(signalName);
    assertThat(instancesCreated(activeId, markerKey)).isEqualTo(1);
    assertThat(instancesCreated(suspendedId, markerKey)).isZero();

    // when - the definition is resumed and the signal is broadcast again
    resume(suspendedKey);
    engine.signal().withSignalName(signalName).broadcast();
    final long resumedMarkerKey = completeMarkerInstance();

    // then - the resumed definition starts an instance again
    assertThat(instancesCreated(suspendedId, resumedMarkerKey)).isEqualTo(1);
    assertThat(instancesCreated(activeId, resumedMarkerKey)).isEqualTo(2);
  }

  @Test
  public void shouldNotStartInstanceOfSuspendedDefinitionOnConditionButStartOthers() {
    // given - two definitions with a start event on the same condition, one of them suspended
    final var suspendedId = helper.getBpmnProcessId() + "-suspended";
    final var activeId = helper.getBpmnProcessId() + "-active";
    final var suspendedKey = suspend(deploy(suspendedId, start -> start.condition("=x > 1")));
    final var activeKey = deploy(activeId, start -> start.condition("=x > 1"));

    // when
    final var evaluated = engine.conditionalEvaluation().withVariables(Map.of("x", 5)).evaluate();
    final long markerKey = completeMarkerInstance();

    // then - the evaluation succeeded and only reports the instance it started
    assertThat(evaluated.getValue().getStartedProcessInstances())
        .extracting(instance -> instance.getProcessDefinitionKey())
        .containsExactly(activeKey);
    assertThat(instancesCreated(activeId, markerKey)).isEqualTo(1);
    assertThat(instancesCreated(suspendedId, markerKey)).isZero();

    // when - the definition is resumed and the condition is evaluated again
    resume(suspendedKey);
    final var evaluatedAfterResume =
        engine.conditionalEvaluation().withVariables(Map.of("x", 5)).evaluate();
    final long resumedMarkerKey = completeMarkerInstance();

    // then - the resumed definition starts an instance again
    assertThat(evaluatedAfterResume.getValue().getStartedProcessInstances())
        .extracting(instance -> instance.getProcessDefinitionKey())
        .containsExactlyInAnyOrder(suspendedKey, activeKey);
    assertThat(instancesCreated(suspendedId, resumedMarkerKey)).isEqualTo(1);
  }

  private long deploy(final String processId, final UnaryOperator<StartEventBuilder> startEvent) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent("start");
    return engine
        .deployment()
        .withXmlResource(startEvent.apply(builder).endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private long suspend(final long processDefinitionKey) {
    engine.processDefinitionSuspension().withProcessDefinitionKey(processDefinitionKey).suspend();
    return processDefinitionKey;
  }

  private void resume(final long processDefinitionKey) {
    engine.processDefinitionSuspension().withProcessDefinitionKey(processDefinitionKey).resume();
  }

  /**
   * Counts the {@code CREATED} events of started instances. The state transition guard would also
   * reject the activation of a suspended definition, so the creation event is what pins the check.
   *
   * <p>Records are totally ordered, so once a later, unrelated instance has completed, anything the
   * previous commands would have started has already been written.
   */
  private long completeMarkerInstance() {
    final var markerId = helper.getBpmnProcessId() + "-marker";
    engine
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(markerId).startEvent().endEvent().done())
        .deploy();
    final long markerKey = engine.processInstance().ofBpmnProcessId(markerId).create();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(markerKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    return markerKey;
  }

  private long instancesCreated(final String processId, final long markerKey) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED && r.getKey() == markerKey)
        .asList()
        .stream()
        .filter(r -> r.getIntent() == ProcessInstanceCreationIntent.CREATED)
        .map(Record::getValue)
        .map(ProcessInstanceCreationRecordValue.class::cast)
        .filter(value -> value.getBpmnProcessId().equals(processId))
        .count();
  }
}
