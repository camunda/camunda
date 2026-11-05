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
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Start event subscriptions (message, signal, timer) belong to the latest active, non-suspended
 * version of a process. Suspend, resume, deploy and delete hand them over.
 */
@RunWith(Parameterized.class)
public class ProcessDefinitionSuspensionStartEventTest {

  enum StartType {
    MESSAGE,
    SIGNAL,
    TIMER
  }

  @Parameter public StartType startType;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String processId;

  @Parameters(name = "{0}")
  public static Object[] startTypes() {
    return StartType.values();
  }

  private void init() {
    processId = Strings.newRandomValidBpmnId();
  }

  private long deployVersion(final String label) {
    StartEventBuilder start = Bpmn.createExecutableProcess(processId).startEvent("start");
    start =
        switch (startType) {
          case MESSAGE -> start.message(processId);
          case SIGNAL -> start.signal(processId);
          case TIMER -> start.timerWithCycle("R/PT1H");
        };
    return engine
        .deployment()
        .withXmlResource(
            start.serviceTask("task", t -> t.zeebeJobType("task-" + label)).endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  /** Deploys an unrelated process and returns its position, which follows all earlier records. */
  private long sync() {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(Strings.newRandomValidBpmnId())
                .startEvent()
                .endEvent()
                .done())
        .deploy()
        .getPosition();
  }

  /** The process definition keys that currently hold an open start event subscription. */
  private Set<Long> owners() {
    final long syncPosition = sync();
    final List<? extends Record<?>> records =
        RecordingExporter.records().limit(r -> r.getPosition() >= syncPosition).toList();
    final Map<Long, Integer> open = new HashMap<>();
    for (final Record<?> r : records) {
      switch (startType) {
        case MESSAGE -> {
          if (r.getValueType() == ValueType.MESSAGE_START_EVENT_SUBSCRIPTION
              && ((MessageStartEventSubscriptionRecordValue) r.getValue())
                  .getMessageName()
                  .equals(processId)) {
            count(
                open,
                ((MessageStartEventSubscriptionRecordValue) r.getValue()).getProcessDefinitionKey(),
                r.getIntent() == MessageStartEventSubscriptionIntent.CREATED,
                r.getIntent() == MessageStartEventSubscriptionIntent.DELETED);
          }
        }
        case SIGNAL -> {
          if (r.getValueType() == ValueType.SIGNAL_SUBSCRIPTION
              && ((SignalSubscriptionRecordValue) r.getValue()).getSignalName().equals(processId)) {
            count(
                open,
                ((SignalSubscriptionRecordValue) r.getValue()).getProcessDefinitionKey(),
                r.getIntent() == SignalSubscriptionIntent.CREATED,
                r.getIntent() == SignalSubscriptionIntent.DELETED);
          }
        }
        case TIMER -> {
          if (r.getValueType() == ValueType.TIMER
              && ((TimerRecordValue) r.getValue()).getTargetElementId().equals("start")
              && ((TimerRecordValue) r.getValue()).getProcessInstanceKey() <= 0) {
            count(
                open,
                ((TimerRecordValue) r.getValue()).getProcessDefinitionKey(),
                r.getIntent() == TimerIntent.CREATED,
                r.getIntent() == TimerIntent.CANCELED);
          }
        }
        default -> throw new IllegalStateException("Unexpected start type " + startType);
      }
    }
    final Set<Long> result = new HashSet<>();
    open.forEach(
        (key, n) -> {
          if (n > 0) {
            result.add(key);
          }
        });
    return result;
  }

  private static void count(
      final Map<Long, Integer> open, final long key, final boolean opened, final boolean closed) {
    if (opened) {
      open.merge(key, 1, Integer::sum);
    } else if (closed) {
      open.merge(key, -1, Integer::sum);
    }
  }

  @Test
  public void shouldMoveSubscriptionsToPreviousVersionOnSuspend() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");
    assertThat(owners()).containsExactly(v2);

    // when
    engine.processDefinitionSuspension().withKey(v2).suspend();

    // then
    assertThat(owners()).containsExactly(v1);
  }

  @Test
  public void shouldMoveSubscriptionsBackOnResume() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");
    engine.processDefinitionSuspension().withKey(v2).suspend();
    assertThat(owners()).containsExactly(v1);

    // when
    engine.processDefinitionSuspension().withKey(v2).resume();

    // then
    assertThat(owners()).containsExactly(v2);
  }

  @Test
  public void shouldRemoveSubscriptionsWhenAllVersionsSuspended() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");

    // when
    engine.processDefinitionSuspension().withKey(v2).suspend();
    engine.processDefinitionSuspension().withKey(v1).suspend();

    // then
    assertThat(owners()).isEmpty();
  }

  @Test
  public void shouldNotMoveSubscriptionsWhenSuspendingOlderVersion() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");

    // when
    engine.processDefinitionSuspension().withKey(v1).suspend();

    // then
    assertThat(owners()).containsExactly(v2);
  }

  @Test
  public void shouldKeepOwnerWhenResumingOlderVersion() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");
    engine.processDefinitionSuspension().withKey(v1).suspend();

    // when
    engine.processDefinitionSuspension().withKey(v1).resume();

    // then
    assertThat(owners()).containsExactly(v2);
  }

  @Test
  public void shouldCloseOwnerSubscriptionsWhenDeployingWhileLatestSuspended() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");
    engine.processDefinitionSuspension().withKey(v2).suspend();
    assertThat(owners()).containsExactly(v1);

    // when
    final long v3 = deployVersion("v3");

    // then
    assertThat(owners()).containsExactly(v3);
  }

  @Test
  public void shouldSkipSuspendedVersionOnDeletionHandover() {
    // given
    init();
    final long v1 = deployVersion("v1");
    final long v2 = deployVersion("v2");
    engine.processDefinitionSuspension().withKey(v1).suspend();

    // when
    engine.resourceDeletion().withResourceKey(v2).delete();

    // then
    assertThat(owners()).isEmpty();
  }

  @Test
  public void shouldStartOnlyLatestActiveVersionOnTrigger() {
    // given
    init();
    deployVersion("v1");
    final long v2 = deployVersion("v2");
    engine.processDefinitionSuspension().withKey(v2).suspend();
    final long v3 = deployVersion("v3");

    // when
    switch (startType) {
      case MESSAGE -> engine.message().withName(processId).withCorrelationKey("").publish();
      case SIGNAL -> engine.signal().withSignalName(processId).broadcast();
      case TIMER -> {
        return; // timers are covered by the owner assertions; the clock is not advanced here
      }
      default -> throw new IllegalStateException("Unexpected start type " + startType);
    }

    // then
    final long syncPosition = sync();
    final List<? extends Record<?>> records =
        RecordingExporter.records().limit(r -> r.getPosition() >= syncPosition).toList();
    final List<ProcessInstanceRecordValue> started =
        records.stream()
            .filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE)
            .filter(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .map(r -> (ProcessInstanceRecordValue) r.getValue())
            .filter(v -> v.getBpmnProcessId().equals(processId))
            .filter(v -> v.getBpmnElementType() == BpmnElementType.PROCESS)
            .toList();
    assertThat(started).hasSize(1);
    assertThat(started.getFirst().getProcessDefinitionKey()).isEqualTo(v3);
  }
}
