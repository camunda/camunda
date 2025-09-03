/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.db.rdbms.write.domain.MessageCorrelationDbModel;
import io.camunda.db.rdbms.write.service.MessageCorrelationWriter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageCorrelationExportHandlerTest {

  @Mock private MessageCorrelationWriter messageCorrelationWriter;

  @Captor private ArgumentCaptor<MessageCorrelationDbModel> modelCaptor;

  private MessageCorrelationExportHandler handler;
  private final ProtocolFactory factory = new ProtocolFactory();

  @BeforeEach
  void setUp() {
    handler = new MessageCorrelationExportHandler(messageCorrelationWriter);
  }

  @Test
  void shouldHandleProcessMessageSubscriptionCorrelatedEvent() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(ProcessMessageSubscriptionIntent.CORRELATED)
                    .withValue(factory.generateObject(ProcessMessageSubscriptionRecordValue.class)));

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
    assertThat(model.flowNodeInstanceKey()).isEqualTo(record.getValue().getElementInstanceKey());
    assertThat(model.flowNodeId()).isEqualTo(record.getValue().getElementId());
    assertThat(model.processDefinitionId()).isEqualTo(record.getValue().getBpmnProcessId());
    assertThat(model.tenantId()).isEqualTo(record.getValue().getTenantId());
    assertThat(model.partitionId()).isEqualTo(record.getPartitionId());

    verifyNoMoreInteractions(messageCorrelationWriter);
  }

  @Test
  void shouldNotHandleNonCorrelatedEvents() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED));

    // when
    final boolean canExport = handler.canExport(record);

    // then
    assertThat(canExport).isFalse();
    verifyNoMoreInteractions(messageCorrelationWriter);
  }
}