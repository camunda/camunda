/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class MessageStartProcessInstanceRequestRecordTest {

  @Test
  void shouldExposeIdentityDefaults() {
    // given
    final MessageStartProcessInstanceRequestRecord record =
        new MessageStartProcessInstanceRequestRecord();

    // then — defaults must let the SBE-decoded record represent an "absent field" state safely
    assertThat(record.getMessageKey()).isEqualTo(-1L);
    assertThat(record.getMessageName()).isEmpty();
    assertThat(record.getCorrelationKey()).isEmpty();
    assertThat(record.getBusinessId()).isEmpty();
    assertThat(record.getProcessDefinitionKey()).isEqualTo(-1L);
    assertThat(record.getBpmnProcessId()).isEmpty();
    assertThat(record.getStartEventId()).isEmpty();
    assertThat(record.getMessageStartEventSubscriptionKey()).isEqualTo(-1L);
    assertThat(record.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(record.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(record.getVariables()).isEmpty();
  }

  @Test
  void shouldRoundTripAllFieldsViaMsgPack() {
    // given — populate every field of the wire shape so the round-trip catches a missing
    // declareProperty or a missing setter just as well as a serialisation regression
    final MessageStartProcessInstanceRequestRecord original =
        new MessageStartProcessInstanceRequestRecord()
            .setMessageKey(2251799813685248L)
            .setMessageName("OrderPlaced")
            .setCorrelationKey("order-42")
            .setBusinessId("BIZ-42")
            .setProcessDefinitionKey(2251799813685000L)
            .setBpmnProcessId("order-process")
            .setStartEventId("start_OrderPlaced")
            .setMessageStartEventSubscriptionKey(2251799813685100L)
            .setVariables(
                new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{\"orderId\":\"order-42\"}")))
            .setProcessInstanceKey(2251799813685300L)
            .setTenantId("acme");

    // when
    final MessageStartProcessInstanceRequestRecord copy =
        new MessageStartProcessInstanceRequestRecord();
    copy.copyFrom(original);

    // then
    assertThat(copy.getMessageKey()).isEqualTo(original.getMessageKey());
    assertThat(copy.getMessageName()).isEqualTo(original.getMessageName());
    assertThat(copy.getCorrelationKey()).isEqualTo(original.getCorrelationKey());
    assertThat(copy.getBusinessId()).isEqualTo(original.getBusinessId());
    assertThat(copy.getProcessDefinitionKey()).isEqualTo(original.getProcessDefinitionKey());
    assertThat(copy.getBpmnProcessId()).isEqualTo(original.getBpmnProcessId());
    assertThat(copy.getStartEventId()).isEqualTo(original.getStartEventId());
    assertThat(copy.getMessageStartEventSubscriptionKey())
        .isEqualTo(original.getMessageStartEventSubscriptionKey());
    assertThat(copy.getProcessInstanceKey()).isEqualTo(original.getProcessInstanceKey());
    assertThat(copy.getTenantId()).isEqualTo(original.getTenantId());
    assertThat(copy.getVariables()).containsExactlyEntriesOf(Map.of("orderId", "order-42"));
  }

  @Test
  void shouldRoundTripWithoutOptionalReplyFields() {
    // given — request-shaped record: no processInstanceKey, no variables override
    final MessageStartProcessInstanceRequestRecord original =
        new MessageStartProcessInstanceRequestRecord()
            .setMessageKey(7L)
            .setMessageName("OrderPlaced")
            .setCorrelationKey("order-7")
            .setBusinessId("BIZ-7")
            .setProcessDefinitionKey(42L)
            .setBpmnProcessId("order-process")
            .setStartEventId("start_OrderPlaced")
            .setMessageStartEventSubscriptionKey(8L)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when
    final MessageStartProcessInstanceRequestRecord copy =
        new MessageStartProcessInstanceRequestRecord();
    copy.copyFrom(original);

    // then — defaults survive the round-trip; this pins forward-compat between request and reply
    assertThat(copy.getProcessInstanceKey()).isEqualTo(-1L);
    assertThat(copy.getVariables()).isEmpty();
    assertThat(copy.getMessageKey()).isEqualTo(7L);
    assertThat(copy.getBusinessId()).isEqualTo("BIZ-7");
  }
}
