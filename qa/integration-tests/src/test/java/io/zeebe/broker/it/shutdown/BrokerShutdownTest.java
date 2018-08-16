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
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.TomlConfigurationReader;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.ServerSocketBinding;
import io.zeebe.util.FileUtil;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.assertj.core.util.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BrokerShutdownTest {

  private static final ServiceName<Void> BLOCK_BROKER_SERVICE_NAME =
      ServiceName.newServiceName("blockService", Void.class);

  @Rule public ExpectedException exception = ExpectedException.none();
  private File brokerBase;
  private Broker broker;

  @Before
  public void setup() {
    brokerBase = Files.newTemporaryFolder();
    broker = startBrokerWithBlockingService(brokerBase);
  }

  @After
  public void tearDown() throws IOException {
    FileUtil.deleteFolder(brokerBase.getAbsolutePath());
  }

  @Test
  public void shouldReleaseSockets() {
    // given
    broker.getBrokerContext().setCloseTimeout(Duration.ofSeconds(1));

    // when
    broker.close();

    // then
    final NetworkCfg networkCfg = broker.getBrokerContext().getBrokerConfiguration().getNetwork();

    tryToBindSocketAddress(networkCfg.getManagement().toSocketAddress());
    tryToBindSocketAddress(networkCfg.getReplication().toSocketAddress());
    tryToBindSocketAddress(networkCfg.getClient().toSocketAddress());
  }

  private void tryToBindSocketAddress(SocketAddress socketAddress) {
    final InetSocketAddress socket = socketAddress.toInetSocketAddress();
    final ServerSocketBinding binding = new ServerSocketBinding(socket);
    binding.doBind();
    binding.close();
  }

  private Broker startBrokerWithBlockingService(final File brokerBase) {
    final Broker broker;
    try (InputStream configStream =
        EmbeddedBrokerRule.class.getResourceAsStream("/zeebe.default.cfg.toml")) {
      final BrokerCfg brokerCfg = TomlConfigurationReader.read(configStream);
      EmbeddedBrokerRule.assignSocketAddresses(brokerCfg);
      broker = new Broker(brokerCfg, brokerBase.getAbsolutePath(), null);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to open configuration", e);
    }

    final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

    try {
      // blocks on shutdown
      serviceContainer
          .createService(BLOCK_BROKER_SERVICE_NAME, new BlockingService())
          .dependency(
              TransportServiceNames.bufferingServerTransport(
                  TransportServiceNames.MANAGEMENT_API_SERVER_NAME))
          .dependency(
              TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
          .dependency(
              TransportServiceNames.serverTransport(
                  TransportServiceNames.REPLICATION_API_SERVER_NAME))
          .install()
          .get(25, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException(
          "System partition not installed into the container withing 25 seconds.");
    }
    return broker;
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
