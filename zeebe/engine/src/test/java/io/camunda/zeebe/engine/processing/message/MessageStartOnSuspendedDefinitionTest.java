/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that a message start event never creates an instance of a suspended process definition:
 * neither when a message is published, nor when a buffered message is picked up after an instance
 * completes. Publishing itself still succeeds and the message stays buffered, while a synchronous
 * correlate is rejected with {@code NOT_FOUND}.
 */
public final class MessageStartOnSuspendedDefinitionTest {

  private static final Duration TTL = Duration.ofMinutes(10);

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldNotStartInstanceWhenPublishingToSuspendedDefinition() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var messageName = helper.getMessageName();
    final var metadata = deployMessageStartProcess(processId, messageName);
    suspend(metadata);

    // when
    final var published =
        engine
            .message()
            .withName(messageName)
            .withCorrelationKey(helper.getCorrelationValue())
            .withTimeToLive(TTL)
            .publish();
    final long markerKey = completeMarkerInstance();

    // then - publishing succeeded, but nothing was started or correlated
    assertThat(published.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
    assertThat(processInstancesActivated(processId, markerKey)).isZero();
    assertThat(
            recordsUntilMarkerCompleted(markerKey)
                .messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                .withMessageName(messageName)
                .exists())
        .isFalse();
  }

  @Test
  public void shouldNotStartInstanceFromBufferedMessageWhenDefinitionIsSuspended() {
    // given - a running instance holding the correlation key, and a second message buffered
    final var processId = helper.getBpmnProcessId();
    final var messageName = helper.getMessageName();
    final var correlationKey = helper.getCorrelationValue();
    final var metadata = deployMessageStartProcess(processId, messageName);
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(TTL)
        .publish();
    final long firstInstanceKey =
        RecordingExporter.processInstanceRecords()
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withBpmnProcessId(processId)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst()
            .getKey();
    engine
        .message()
        .withName(messageName)
        .withCorrelationKey(correlationKey)
        .withTimeToLive(TTL)
        .publish();
    suspend(metadata);

    // when - the first instance completes, which would normally pick up the buffered message
    engine.job().ofInstance(firstInstanceKey).withType(helper.getJobType()).complete();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(firstInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .await();
    final long markerKey = completeMarkerInstance();

    // then - no second instance was started, and the buffered message was not correlated: the
    // state transition guard would also stop the activation, so CORRELATED is what pins the check
    assertThat(processInstancesActivated(processId, markerKey)).isEqualTo(1);
    assertThat(
            recordsUntilMarkerCompleted(markerKey)
                .messageStartEventSubscriptionRecords()
                .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                .withMessageName(messageName)
                .count())
        .isEqualTo(1);
  }

  /**
   * A single partition guarantees the correlation key and the start event share a partition, so
   * nothing is delegated: the {@code -1} from a start that could not be created must reject the
   * correlate with {@code NOT_FOUND}, the same as for a draining definition, and not defer it.
   */
  @Test
  public void shouldRejectCorrelateNotFoundWhenDefinitionIsSuspended() {
    // given
    final var messageName = helper.getMessageName();
    final var metadata = deployMessageStartProcess(helper.getBpmnProcessId(), messageName);
    suspend(metadata);

    // when - no businessId, so there is no cross-partition arm
    final var rejection =
        engine
            .messageCorrelation()
            .withName(messageName)
            .withCorrelationKey("")
            .expectRejection()
            .correlate();

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  private ProcessMetadataValue deployMessageStartProcess(
      final String processId, final String messageName) {
    final var metadata =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent("start")
                    .message(messageName)
                    .serviceTask("task", t -> t.zeebeJobType(helper.getJobType()))
                    .endEvent()
                    .done())
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .getFirst();
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(messageName)
        .await();
    return metadata;
  }

  private void suspend(final ProcessMetadataValue metadata) {
    engine
        .processDefinitionSuspension()
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .suspend();
  }

  /**
   * Records are totally ordered, so once a later, unrelated instance has completed, anything the
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

  private RecordStream recordsUntilMarkerCompleted(final long markerKey) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED
                    && r.getKey() == markerKey);
  }

  private long processInstancesActivated(final String processId, final long markerKey) {
    return recordsUntilMarkerCompleted(markerKey)
        .processInstanceRecords()
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withBpmnProcessId(processId)
        .withElementType(BpmnElementType.PROCESS)
        .count();
  }
}
