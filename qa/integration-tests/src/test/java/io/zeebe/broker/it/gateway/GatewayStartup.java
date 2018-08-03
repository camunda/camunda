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
package io.zeebe.broker.it.gateway;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.fail;

import io.grpc.StatusRuntimeException;
import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.clustering.ClusteringRule;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.Topology;
import io.zeebe.test.util.AutoCloseableRule;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class GatewayStartup {

  public AutoCloseableRule closeables = new AutoCloseableRule();
  public Timeout testTimeout = Timeout.seconds(60);
  public ClientRule clientRule = new ClientRule();
  public ClusteringRule clusteringRule = new ClusteringRule(closeables, clientRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(closeables).around(testTimeout).around(clientRule).around(clusteringRule);

  private String broker1Address;
  private String broker2Address;
  private String broker3Address;

  @Before
  public void setUp() {
    this.broker1Address =
        clusteringRule
            .getBrokerConfig(ClusteringRule.BROKER_1_CLIENT_ADDRESS)
            .getNetwork()
            .getGateway()
            .toSocketAddress()
            .toString();

    this.broker2Address =
        clusteringRule
            .getBrokerConfig(ClusteringRule.BROKER_2_CLIENT_ADDRESS)
            .getNetwork()
            .getGateway()
            .toSocketAddress()
            .toString();

    this.broker3Address =
        clusteringRule
            .getBrokerConfig(ClusteringRule.BROKER_3_CLIENT_ADDRESS)
            .getNetwork()
            .getGateway()
            .toSocketAddress()
            .toString();
  }

  @Test
  public void shouldCheckAvailabilityOfGateways() throws InterruptedException {
    final ZeebeClient client =
        ZeebeClient.newClientBuilder().brokerContactPoint(this.broker1Address).build();
    final Topology response = client.newTopologyRequest().send().join();
    assertThat(response).isNotNull();

    final ZeebeClient client2 =
        ZeebeClient.newClientBuilder()
            .requestTimeout(Duration.ofMillis(50))
            .brokerContactPoint(this.broker2Address)
            .build();

    final ZeebeClient client3 =
        ZeebeClient.newClientBuilder()
            .requestTimeout(Duration.ofMillis(50))
            .brokerContactPoint(this.broker3Address)
            .build();

    try {
      client2.newTopologyRequest().send().join();
      fail("gateway should not be enabled");
    } catch (final Exception e) {
      assertThat(e)
          .isInstanceOf(StatusRuntimeException.class)
          .hasMessageContaining("UNAVAILABLE: io exception");
    }

    try {
      client3.newTopologyRequest().send().join();
      fail("gateway should not be enabled");
    } catch (final Exception e) {
      assertThat(e)
          .isInstanceOf(StatusRuntimeException.class)
          .hasMessageContaining("UNAVAILABLE: io exception");
    }
  }
}
