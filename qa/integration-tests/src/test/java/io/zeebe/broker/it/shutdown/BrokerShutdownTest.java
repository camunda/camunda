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
package io.zeebe.broker.it.shutdown;

import io.zeebe.broker.Broker;
import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.net.InetSocketAddress;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class BrokerShutdownTest {

  private static final ServiceName<Void> BLOCK_BROKER_SERVICE_NAME =
      ServiceName.newServiceName("blockService", Void.class);

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public Timeout timeout = Timeout.seconds(60);

  @Test
  public void shouldReleaseSockets() {
    // given
    brokerRule.installService(
        serviceContainer ->
            serviceContainer
                .createService(BLOCK_BROKER_SERVICE_NAME, new BlockingService())
                .dependency(
                    TransportServiceNames.serverTransport(
                        TransportServiceNames.CLIENT_API_SERVER_NAME)));

    final Broker broker = brokerRule.getBroker();
    broker.getBrokerContext().setCloseTimeout(Duration.ofSeconds(1));

    // when
    broker.close();

    // then
    final NetworkCfg networkCfg = broker.getBrokerContext().getBrokerConfiguration().getNetwork();

    tryToBindSocketAddress(networkCfg.getClient().toSocketAddress());
  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }

  private class BlockingService implements Service<Void> {
    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {
      final CompletableActorFuture<Void> neverCompletingFuture = new CompletableActorFuture<>();
      stopContext.async(neverCompletingFuture);
    }

    @Override
    public Void get() {
      return null;
    }
  }
}
