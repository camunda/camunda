/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.test;

import io.zeebe.broker.Broker;
import io.zeebe.broker.TestLoggers;
import io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.system.configuration.SocketBindingCfg;
import io.zeebe.broker.system.configuration.TomlConfigurationReader;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;

public class EmbeddedBrokerRule extends ExternalResource {

  private static final Consumer<BrokerCfg> NOOP_CONFIGURATOR = cfg -> {};

  private static final String SNAPSHOTS_DIRECTORY = "snapshots";

  protected static final Logger LOG = TestLoggers.TEST_LOGGER;

  protected BrokerCfg brokerCfg;
  protected Broker broker;

  protected ControlledActorClock controlledActorClock = new ControlledActorClock();

  protected final Supplier<InputStream> configSupplier;
  protected final Consumer<BrokerCfg> configurator;

  public EmbeddedBrokerRule() {
    this(NOOP_CONFIGURATOR);
  }

  public EmbeddedBrokerRule(String configFileClasspathLocation) {
    this(configFileClasspathLocation, NOOP_CONFIGURATOR);
  }

  public EmbeddedBrokerRule(Consumer<BrokerCfg> configurator) {
    this("zeebe.unit-test.cfg.toml", configurator);
  }

  public EmbeddedBrokerRule(String configFileClasspathLocation, Consumer<BrokerCfg> configurator) {
    this(
        () ->
            EmbeddedBrokerRule.class
                .getClassLoader()
                .getResourceAsStream(configFileClasspathLocation),
        configurator);
  }

  public EmbeddedBrokerRule(
      Supplier<InputStream> configSupplier, Consumer<BrokerCfg> configurator) {
    this.configSupplier = configSupplier;
    this.configurator = configurator;
  }

  protected long startTime;

  private File newTemporaryFolder;
  private String[] dataDirectories;

  @Override
  protected void before() {
    newTemporaryFolder = Files.newTemporaryFolder();
    startTime = System.currentTimeMillis();
    startBroker();
    LOG.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));
    startTime = System.currentTimeMillis();
  }

  @Override
  protected void after() {
    try {
      LOG.info("Test execution time: " + (System.currentTimeMillis() - startTime));
      startTime = System.currentTimeMillis();
      stopBroker();
      LOG.info("Broker closing time: " + (System.currentTimeMillis() - startTime));

      final long allocatedMemoryInKb = DirectBufferAllocator.getAllocatedMemoryInKb();
      if (allocatedMemoryInKb > 0) {
        LOG.warn(
            "There are still allocated direct buffers of a total size of {}kB.",
            allocatedMemoryInKb);
      }
    } finally {
      try {
        FileUtil.deleteFolder(newTemporaryFolder.getAbsolutePath());
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  public SocketAddress getClientAddress() {
    return brokerCfg.getNetwork().getClient().toSocketAddress();
  }

  public SocketAddress getManagementAddress() {
    return brokerCfg.getNetwork().getManagement().toSocketAddress();
  }

  public Broker getBroker() {
    return this.broker;
  }

  public ControlledActorClock getClock() {
    return controlledActorClock;
  }

  public void restartBroker() {
    stopBroker();
    startBroker();
  }

  public void stopBroker() {
    broker.close();
    broker = null;
    System.gc();
  }

  public void startBroker() {
    if (brokerCfg == null) {
      try (InputStream configStream = configSupplier.get()) {
        brokerCfg = TomlConfigurationReader.read(configStream);
        configurator.accept(brokerCfg);
        assignSocketAddresses(brokerCfg);
      } catch (final IOException e) {
        throw new RuntimeException("Unable to open configuration", e);
      }
    }

    broker = new Broker(brokerCfg, newTemporaryFolder.getAbsolutePath(), controlledActorClock);

    final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

    try {
      // Hack: block until the system stream processor is available
      // this is required in the broker-test suite, because the client rule does not perform request
      // retries
      // How to make it better: https://github.com/zeebe-io/zeebe/issues/196
      final String systemTopicName = Protocol.SYSTEM_TOPIC + "-" + Protocol.SYSTEM_PARTITION;

      serviceContainer
          .createService(TestService.NAME, new TestService())
          .dependency(ClusterBaseLayerServiceNames.leaderPartitionServiceName(systemTopicName))
          .dependency(
              TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
          .install()
          .get(25, TimeUnit.SECONDS);

    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      stopBroker();
      throw new RuntimeException(
          "System patition not installed into the container withing 25 seconds.");
    }

    dataDirectories = broker.getBrokerContext().getBrokerConfiguration().getData().getDirectories();
  }

  public void purgeSnapshots() {
    for (String dataDirectoryName : dataDirectories) {
      final File dataDirectory = new File(dataDirectoryName);

      final File[] partitionDirectories =
          dataDirectory.listFiles((d, f) -> new File(d, f).isDirectory());

      for (File partitionDirectory : partitionDirectories) {
        final File snapshotDirectory = new File(partitionDirectory, SNAPSHOTS_DIRECTORY);

        if (snapshotDirectory.exists()) {
          try {
            FileUtil.deleteFolder(snapshotDirectory.getAbsolutePath());
          } catch (IOException e) {
            throw new RuntimeException(
                "Could not delete snapshot directory " + snapshotDirectory.getAbsolutePath(), e);
          }
        }
      }
    }
  }

  public <T> void removeService(ServiceName<T> name) {
    try {
      broker.getBrokerContext().getServiceContainer().removeService(name).get(10, TimeUnit.SECONDS);
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException("Could not remove service " + name.getName() + " in 10 seconds.");
    }
  }

  public void assignSocketAddresses(BrokerCfg brokerCfg) {
    final NetworkCfg network = brokerCfg.getNetwork();
    final List<SocketBindingCfg> socketBindingCfgs =
        Arrays.asList(
            network.getClient(),
            network.getGateway(),
            network.getManagement(),
            network.getSubscription(),
            network.getReplication());

    if (network.getPortOffset() > 0) {
      throw new UnsupportedOperationException(
          "Please don't set the port offset in a test configuration");
    }

    for (SocketBindingCfg socketBindingCfg : socketBindingCfgs) {
      final SocketAddress address = SocketUtil.getNextAddress();
      socketBindingCfg.setPort(address.port());
    }
  }

  static class TestService implements Service<TestService> {

    static final ServiceName<TestService> NAME =
        ServiceName.newServiceName("testService", TestService.class);

    @Override
    public void start(ServiceStartContext startContext) {}

    @Override
    public void stop(ServiceStopContext stopContext) {}

    @Override
    public TestService get() {
      return this;
    }
  }
}
