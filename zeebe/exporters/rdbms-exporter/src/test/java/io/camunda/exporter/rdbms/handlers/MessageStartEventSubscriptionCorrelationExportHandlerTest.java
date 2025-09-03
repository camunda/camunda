/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.MessageCorrelationDbModel;
import io.camunda.db.rdbms.write.service.MessageCorrelationWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageStartEventSubscriptionCorrelationExportHandlerTest {

  @Mock private MessageCorrelationWriter messageCorrelationWriter;

  @Captor private ArgumentCaptor<MessageCorrelationDbModel> modelCaptor;

  private MessageStartEventSubscriptionCorrelationExportHandler handler;
  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeEach
  void setUp() {
    handler = new MessageStartEventSubscriptionCorrelationExportHandler(messageCorrelationWriter);
  }

  @Test
  void shouldHandleMessageStartEventSubscriptionCorrelatedEvent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r ->
                r.withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withValue(factory.generateObject(MessageStartEventSubscriptionRecordValue.class)));

    // when
    final boolean canExport = handler.canExport(record);
    handler.export(record);

    // then
    assertThat(canExport).isTrue();
    verify(messageCorrelationWriter).create(modelCaptor.capture());

    final MessageCorrelationDbModel model = modelCaptor.getValue();
    assertThat(model.subscriptionKey()).isEqualTo(record.getKey());
    assertThat(model.messageKey()).isEqualTo(record.getValue().getMessageKey());
    assertThat(model.messageName()).isEqualTo(record.getValue().getMessageName());
    assertThat(model.correlationKey()).isEqualTo(record.getValue().getCorrelationKey());
    assertThat(model.processInstanceKey()).isEqualTo(record.getValue().getProcessInstanceKey());
    assertThat(model.flowNodeInstanceKey()).isNull(); // Not available for start events
    assertThat(model.flowNodeId()).isEqualTo(record.getValue().getStartEventId());
    assertThat(model.processDefinitionId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(model.processDefinitionKey()).isEqualTo(record.getValue().getProcessDefinitionKey());
    assertThat(model.tenantId()).isEqualTo(record.getValue().getTenantId());
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());

    verifyNoMoreInteractions(messageCorrelationWriter);
  }

  @Test
  void shouldNotHandleNonCorrelatedEvents() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
    verifyNoMoreInteractions(messageCorrelationWriter);
  }
}