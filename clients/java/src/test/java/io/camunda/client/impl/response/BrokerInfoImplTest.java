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
package io.camunda.client.impl.response;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import org.junit.jupiter.api.Test;

final class BrokerInfoImplTest {

  @Test
  void shouldPopulateMemberIdAndZoneFromProto() {
    // given
    final GatewayOuterClass.BrokerInfo proto =
        GatewayOuterClass.BrokerInfo.newBuilder()
            .setNodeId(0)
            .setBrokerId("us-east_0")
            .setZone("us-east")
            .setHost("localhost")
            .setPort(26501)
            .setVersion("1.0.0")
            .build();

    // when
    final BrokerInfoImpl brokerInfo = new BrokerInfoImpl(proto);

    // then
    assertThat(brokerInfo.getMemberId()).isEqualTo("us-east_0");
    assertThat(brokerInfo.getZone()).isEqualTo("us-east");
  }

  @Test
  void shouldReturnNullZoneWhenProtoZoneIsEmpty() {
    // given -- proto sends "" for absent zone (protobuf string default)
    final GatewayOuterClass.BrokerInfo proto =
        GatewayOuterClass.BrokerInfo.newBuilder()
            .setNodeId(0)
            .setBrokerId("0")
            .setZone("")
            .setHost("localhost")
            .setPort(26501)
            .setVersion("1.0.0")
            .build();

    // when
    final BrokerInfoImpl brokerInfo = new BrokerInfoImpl(proto);

    // then
    assertThat(brokerInfo.getZone()).isNull();
  }

  @Test
  void shouldFallBackToNodeIdWhenProtoMemberIdIsEmpty() {
    // given -- old broker without memberId sends ""
    final GatewayOuterClass.BrokerInfo proto =
        GatewayOuterClass.BrokerInfo.newBuilder()
            .setNodeId(3)
            .setBrokerId("")
            .setHost("localhost")
            .setPort(26501)
            .setVersion("1.0.0")
            .build();

    // when
    final BrokerInfoImpl brokerInfo = new BrokerInfoImpl(proto);

    // then
    assertThat(brokerInfo.getMemberId()).isEqualTo("3");
    assertThat(brokerInfo.getZone()).isNull();
  }
}
