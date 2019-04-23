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
package io.zeebe.test;

import io.zeebe.broker.Broker;
import io.zeebe.broker.clustering.base.partitions.Partition;
import io.zeebe.broker.clustering.base.partitions.PartitionServiceNames;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.protocol.Protocol;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.impl.util.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.TomlConfigurationReader;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public class EmbeddedBrokerRule extends ExternalResource {

  public static final String DEFAULT_CONFIG_FILE = "zeebe.test.cfg.toml";
  public static final int DEFAULT_TIMEOUT = 25;
  public static final String TEST_RECORD_EXPORTER_ID = "test-recorder";
  protected static final Logger LOG = new ZbLogger("io.zeebe.test");
  private static final String SNAPSHOTS_DIRECTORY = "snapshots";
  private static final String STATE_DIRECTORY = "state";
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  protected final BrokerCfg brokerCfg;
  protected final ControlledActorClock controlledActorClock = new ControlledActorClock();
  protected final Supplier<InputStream> configSupplier;
  protected final Consumer<BrokerCfg>[] configurators;
  private final int timeout;
  private final File newTemporaryFolder;

  protected Broker broker;
  protected long startTime;
  private List<String> dataDirectories;

  @SafeVarargs
  public EmbeddedBrokerRule(Consumer<BrokerCfg>... configurators) {
    this(DEFAULT_CONFIG_FILE, configurators);
  }

  @SafeVarargs
  public EmbeddedBrokerRule(
      final String configFileClasspathLocation, Consumer<BrokerCfg>... configurators) {
    this(
        () ->
            EmbeddedBrokerRule.class
                .getClassLoader()
                .getResourceAsStream(configFileClasspathLocation),
        DEFAULT_TIMEOUT,
        configurators);
  }

  @SafeVarargs
  public EmbeddedBrokerRule(
      final Supplier<InputStream> configSupplier,
      int timeout,
      final Consumer<BrokerCfg>... configurators) {
    this.configSupplier = configSupplier;
    this.configurators = configurators;
    this.timeout = timeout;

    newTemporaryFolder = Files.newTemporaryFolder();
    try (InputStream configStream = configSupplier.get()) {
      if (configStream == null) {
        brokerCfg = new BrokerCfg();
      } else {
        brokerCfg = TomlConfigurationReader.read(configStream, BrokerCfg.class);
      }
      configureBroker(brokerCfg);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to open configuration", e);
    }
  }

  private static void deleteSnapshots(final File parentDir) {
    final File snapshotDirectory = new File(parentDir, SNAPSHOTS_DIRECTORY);

    if (snapshotDirectory.exists()) {
      try {
        FileUtil.deleteFolder(snapshotDirectory.getAbsolutePath());
      } catch (final IOException e) {
        throw new RuntimeException(
            "Could not delete snapshot directory " + snapshotDirectory.getAbsolutePath(), e);
      }
    }
  }

  public static void assignSocketAddresses(final BrokerCfg brokerCfg) {
    final NetworkCfg network = brokerCfg.getNetwork();
    brokerCfg.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().port());
    network.getClient().setPort(SocketUtil.getNextAddress().port());
    network.getAtomix().setPort(SocketUtil.getNextAddress().port());
    brokerCfg.getMetrics().setPort(SocketUtil.getNextAddress().port());
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  protected void before() {
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
      } catch (final IOException e) {
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

  public SocketAddress getGatewayAddress() {
    return brokerCfg.getGateway().getNetwork().toSocketAddress();
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
    if (broker != null) {
      broker.close();
      broker = null;
      System.gc();
    }
  }

  public void startBroker() {
    startTime = System.currentTimeMillis();
    broker = new Broker(brokerCfg, newTemporaryFolder.getAbsolutePath(), controlledActorClock);

    final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

    try {
      // Hack: block until the system stream processor is available
      // this is required in the broker-test suite, because the client rule does not perform request
      // retries
      // How to make it better: https://github.com/zeebe-io/zeebe/issues/196
      final String partitionName = Partition.getPartitionName(Protocol.DEPLOYMENT_PARTITION);

      serviceContainer
          .createService(TestService.NAME, new TestService())
          .dependency(PartitionServiceNames.leaderPartitionServiceName(partitionName))
          .dependency(
              TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME))
          .install()
          .get(timeout, TimeUnit.SECONDS);

    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      stopBroker();
      throw new RuntimeException(
          String.format(
              "System partition not installed into the container withing %d seconds.", timeout),
          e);
    }

    dataDirectories = broker.getBrokerContext().getBrokerConfiguration().getData().getDirectories();
  }

  public void configureBroker(final BrokerCfg brokerCfg) {
    // build-in exporters
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setId(TEST_RECORD_EXPORTER_ID);
    exporterCfg.setClassName(RecordingExporter.class.getName());
    brokerCfg.getExporters().add(exporterCfg);

    // custom configurators
    for (Consumer<BrokerCfg> configurator : configurators) {
      configurator.accept(brokerCfg);
    }

    // set random port numbers
    assignSocketAddresses(brokerCfg);
  }

  public void purgeSnapshots() {
    for (final String dataDirectoryName : dataDirectories) {
      final File dataDirectory = new File(dataDirectoryName);

      final File[] partitionDirectories =
          dataDirectory.listFiles((d, f) -> new File(d, f).isDirectory());

      for (final File partitionDirectory : partitionDirectories) {
        deleteSnapshots(partitionDirectory);

        final File stateDirectory = new File(partitionDirectory, STATE_DIRECTORY);
        if (stateDirectory.exists()) {
          final File[] stateDirs = stateDirectory.listFiles();
          for (final File processorStateDir : stateDirs) {
            if (processorStateDir.exists()) {
              deleteSnapshots(processorStateDir);
            }
          }
        }
      }
    }
  }

  public <S> S getService(final ServiceName<S> serviceName) {
    final ServiceContainer serviceContainer = broker.getBrokerContext().getServiceContainer();

    final Injector<S> injector = new Injector<>();

    final ServiceName<TestService> accessorServiceName =
        ServiceName.newServiceName("serviceAccess" + serviceName.getName(), TestService.class);
    try {
      serviceContainer
          .createService(accessorServiceName, new TestService())
          .dependency(serviceName, injector)
          .install()
          .get();
    } catch (final InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    serviceContainer.removeService(accessorServiceName);

    return injector.getValue();
  }

  public <T> void removeService(final ServiceName<T> name) {
    try {
      broker.getBrokerContext().getServiceContainer().removeService(name).get(10, TimeUnit.SECONDS);
    } catch (final InterruptedException | ExecutionException | TimeoutException e) {
      throw new RuntimeException("Could not remove service " + name.getName() + " in 10 seconds.");
    }
  }

  static class TestService implements Service<TestService> {

    static final ServiceName<TestService> NAME =
        ServiceName.newServiceName("testService", TestService.class);

    @Override
    public void start(final ServiceStartContext startContext) {}

    @Override
    public void stop(final ServiceStopContext stopContext) {}

    @Override
    public TestService get() {
      return this;
    }
  }
}
