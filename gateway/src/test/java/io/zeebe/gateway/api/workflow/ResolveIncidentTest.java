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

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.api.util.GatewayTest;
import io.zeebe.gateway.impl.broker.request.BrokerResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentResponse;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;
import org.junit.Test;

public class ResolveIncidentTest extends GatewayTest {

  @Test
  public void shouldMapRequestAndResponse() {
    // given
    final ResolveIncidentStub stub = new ResolveIncidentStub();
    stub.registerWith(gateway);

    final ResolveIncidentRequest request =
        ResolveIncidentRequest.newBuilder().setIncidentKey(stub.getIncidentKey()).build();

    // when
    final ResolveIncidentResponse response = client.resolveIncident(request);

    // then
    assertThat(response).isNotNull();

    final BrokerResolveIncidentRequest brokerRequest = gateway.getSingleBrokerRequest();
    assertThat(brokerRequest.getIntent()).isEqualTo(IncidentIntent.RESOLVE);
    assertThat(brokerRequest.getValueType()).isEqualTo(ValueType.INCIDENT);
    assertThat(brokerRequest.getKey()).isEqualTo(stub.getIncidentKey());
  }
}
