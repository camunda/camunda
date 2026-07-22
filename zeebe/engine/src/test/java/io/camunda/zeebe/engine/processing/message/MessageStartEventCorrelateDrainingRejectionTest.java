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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that a synchronous correlate whose only matching message-start subscription cannot start an
 * instance locally (the process definition is draining, so {@link
 * io.camunda.zeebe.engine.processing.common.EventHandle#triggerMessageStartEvent} returns {@code
 * -1}) is rejected with {@code NOT_FOUND} rather than deferred.
 *
 * <p>A single partition guarantees {@code P_B == P_K}, so there is no cross-partition delegation:
 * the {@code -1} returned here means "nothing started locally", not "delegated". If the correlate
 * processor mistook it for a delegation it would defer the response forever — the client would hang
 * and the buffered {@code TTL=-1} correlate message would leak.
 */
public final class MessageStartEventCorrelateDrainingRejectionTest {

  private static final String PROCESS_ID = "wf";
  private static final String MESSAGE_NAME = "start-msg";

  private static final BpmnModelInstance MESSAGE_START_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .message(MESSAGE_NAME)
          .serviceTask("task", t -> t.zeebeJobType("test"))
          .endEvent()
          .done();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldRejectNotFoundWhenLocalStartCannotBeCreated() {
    // given - a deployed message-start process whose definition is then marked DRAINING
    final var metadata =
        engine
            .deployment()
            .withXmlResource(MESSAGE_START_PROCESS)
            .deploy()
            .getValue()
            .getProcessesMetadata()
            .get(0);
    RecordingExporter.messageStartEventSubscriptionRecords(
            MessageStartEventSubscriptionIntent.CREATED)
        .withMessageName(MESSAGE_NAME)
        .await();
    drain(metadata);

    // when - the message is correlated synchronously (no businessId, so no cross-partition arm)
    final var rejection =
        engine
            .messageCorrelation()
            .withName(MESSAGE_NAME)
            .withCorrelationKey("")
            .expectRejection()
            .correlate();

    // then - the correlate is rejected NOT_FOUND instead of being deferred
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
  }

  /**
   * Puts the given process definition into the {@code DRAINING} state. Since no processor writes
   * the {@code DRAINING} event yet, the event is injected onto the log while the engine is stopped
   * so it is applied to state on the next start (replay). TODO(#56978): drive draining via a real
   * {@code RESOURCE_DELETION.DELETE} once that change lands, and remove this injection helper.
   */
  private void drain(final ProcessMetadataValue metadata) {
    engine.stop();
    engine.writeRecords(
        RecordToWrite.event()
            .key(metadata.getProcessDefinitionKey())
            .process(
                ProcessIntent.DRAINING,
                new ProcessRecord()
                    .setKey(metadata.getProcessDefinitionKey())
                    .setBpmnProcessId(metadata.getBpmnProcessId())
                    .setVersion(metadata.getVersion())
                    .setResourceName(metadata.getResourceName())
                    .setTenantId(metadata.getTenantId())));
    engine.start();

    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(metadata.getProcessDefinitionKey())
        .await();
  }
}
