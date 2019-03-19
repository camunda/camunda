/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.gateway.api.workflow;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerPublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.PublishMessageResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.MsgPackUtil;
import java.util.Collections;
import org.junit.Test;

public class PublishMessageTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final PublishMessageStub stub = new PublishMessageStub();
    stub.registerWith(gateway);

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

    final BrokerPublishMessageRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(MessageIntent.PUBLISH);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.MESSAGE);

    final MessageRecord brokerRequestValue = brokerRequest.getRequestWriter();
    assertThat(bufferAsString(brokerRequestValue.getCorrelationKey()))
        .isEqualTo(request.getCorrelationKey());
    assertThat(bufferAsString(brokerRequestValue.getName())).isEqualTo(request.getName());
    assertThat(bufferAsString(brokerRequestValue.getMessageId())).isEqualTo(request.getMessageId());
    assertThat(brokerRequestValue.getTimeToLive()).isEqualTo(request.getTimeToLive());
    MsgPackUtil.assertEqualityExcluding(brokerRequestValue.getVariables(), variables);
  }
}
