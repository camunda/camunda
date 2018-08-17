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
package io.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

import io.zeebe.gateway.factories.DeploymentEventFactory;
import io.zeebe.gateway.factories.TopologyFactory;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.HealthResponse;
import org.junit.Test;

public class ResponseMapperTest {

  @Test
  public void shouldTestHealthCheckMapping() {
    final ResponseMapper responseMapper = new ResponseMapper();
    final TopologyFactory factory = new TopologyFactory();

    final HealthResponse response = responseMapper.toHealthResponse(factory.getFixture());
    assertThat(response.getBrokersCount()).isEqualTo(factory.getBrokersList().size());

    response
        .getBrokersList()
        .forEach(
            broker -> {
              assertTrue(factory.containsBroker(broker));
            });
  }

  @Test
  public void shouldTestDeployWorkflowMapping() {
    final ResponseMapper responseMapper = new ResponseMapper();
    final DeploymentEventFactory factory = new DeploymentEventFactory();

    final DeployWorkflowResponse response =
        responseMapper.toDeployWorkflowResponse(factory.getFixture());
    assertThat(response.getWorkflowsList().size()).isEqualTo(factory.size());

    response
        .getWorkflowsList()
        .forEach(
            workflow -> {
              assertTrue(factory.contains(workflow));
            });
  }
}
