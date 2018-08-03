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
package io.zeebe.client.factories;

import io.zeebe.gateway.protocol.GatewayOuterClass;
import io.zeebe.gateway.protocol.GatewayOuterClass.Partition.PartitionBrokerRole;

public class GrpcBrokerInfoFactory implements TestFactory<GatewayOuterClass.BrokerInfo> {

  @Override
  public GatewayOuterClass.BrokerInfo getFixture() {
    return GatewayOuterClass.BrokerInfo.newBuilder()
        .setPort(4567)
        .setHost("0.0.0.0")
        .addPartitions(
            GatewayOuterClass.Partition.newBuilder()
                .setTopicName("internal-system")
                .setRole(PartitionBrokerRole.LEADER)
                .setPartitionId(0))
        .build();
  }
}
