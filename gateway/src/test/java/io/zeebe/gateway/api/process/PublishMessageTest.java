/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.api.process;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public final class PublishMessageTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final PublishMessageStub stub = new PublishMessageStub();
    stub.registerWith(brokerClient);

    final String variables = JsonUtil.toJson(Collections.singletonMap("key", "value"));

    final PublishMessageRequest request =
        PublishMessageRequest.newBuilder()
            .setCorrelationKey("correlate")
            .setName("message")
            .setMessageId("unique")
            .setTimeToLive(123)
            .setVariables(variables)
            .build();

    // when
    final PublishMessageResponse response = client.publishMessage(request);

    // then
    assertThat(response).isNotNull();

    final BrokerPublishMessageRequest brokerRequest = brokerClient.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(MessageIntent.PUBLISH);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.MESSAGE);

    final MessageRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(bufferAsString(brokerRequestValue.getCorrelationKeyBuffer()))
        .isEqualTo(request.getCorrelationKey());
    assertThat(bufferAsString(brokerRequestValue.getNameBuffer())).isEqualTo(request.getName());
    assertThat(bufferAsString(brokerRequestValue.getMessageIdBuffer()))
        .isEqualTo(request.getMessageId());
    assertThat(brokerRequestValue.getTimeToLive()).isEqualTo(request.getTimeToLive());
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariablesBuffer(), variables);
  }
}
