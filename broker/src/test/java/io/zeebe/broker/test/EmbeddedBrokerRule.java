/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.test;

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.DEBUG_EXPORTER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.HTTP_EXPORTER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setCommandApiPort;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayApiPort;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayClusterPort;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setInternalApiPort;
import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setMonitoringPort;
import static io.zeebe.test.util.TestUtil.waitUntil;

import io.atomix.core.Atomix;
import io.zeebe.broker.Broker;
import io.zeebe.broker.PartitionListener;
import io.zeebe.broker.SpringBrokerBridge;
import io.zeebe.broker.TestLoggers;
import io.zeebe.broker.system.EmbeddedGatewayService;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.test.util.TestConfigurationFactory;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public final class EmbeddedBrokerRule extends ExternalResource {

  public static final String DEFAULT_CONFIG_FILE = "zeebe.test.cfg.yaml";
  public static final int INSTALL_TIMEOUT = 5;
  public static final TimeUnit INSTALL_TIMEOUT_UNIT = TimeUnit.MINUTES;
  protected static final Logger LOG = TestLoggers.TEST_LOGGER;
  private static final boolean ENABLE_DEBUG_EXPORTER = false;
  private static final boolean ENABLE_HTTP_EXPORTER = false;
  private static final String SNAPSHOTS_DIRECTORY = "snapshots";
  private static final String STATE_DIRECTORY = "state";
  protected final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  protected final Supplier<InputStream> configSupplier;
  protected final Consumer<BrokerCfg>[] configurators;
  protected BrokerCfg brokerCfg;
  protected Broker broker;
  protected final ControlledActorClock controlledActorClock = new ControlledActorClock();
  protected final SpringBrokerBridge springBrokerBridge = new SpringBrokerBridge();
  protected long startTime;
  private File newTemporaryFolder;
  private List<String> dataDirectories;

  @SafeVarargs
  public EmbeddedBrokerRule(final Consumer<BrokerCfg>... configurators) {
    this(DEFAULT_CONFIG_FILE, configurators);
  }

  @SafeVarargs
  public EmbeddedBrokerRule(
      final String configFileClasspathLocation, final Consumer<BrokerCfg>... configurators) {
    this(
        () ->
            EmbeddedBrokerRule.class
                .getClassLoader()
                .getResourceAsStream(configFileClasspathLocation),
        configurators);
  }

  @SafeVarargs
  public EmbeddedBrokerRule(
      final Supplier<InputStream> configSupplier, final Consumer<BrokerCfg>... configurators) {
    this.configSupplier = configSupplier;
    this.configurators = configurators;
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
    setGatewayApiPort(SocketUtil.getNextAddress().getPort()).accept(brokerCfg);
    setGatewayClusterPort(SocketUtil.getNextAddress().getPort()).accept(brokerCfg);
    setCommandApiPort(SocketUtil.getNextAddress().getPort()).accept(brokerCfg);
    setInternalApiPort(SocketUtil.getNextAddress().getPort()).accept(brokerCfg);
    setMonitoringPort(SocketUtil.getNextAddress().getPort()).accept(brokerCfg);
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    final Statement statement = recordingExporterTestWatcher.apply(base, description);
    return super.apply(statement, description);
  }

  @Override
  public void before() {
    newTemporaryFolder = Files.newTemporaryFolder();
    startTime = System.currentTimeMillis();
    startBroker();
    LOG.info("\n====\nBroker startup time: {}\n====\n", (System.currentTimeMillis() - startTime));
    startTime = System.currentTimeMillis();
  }

  @Override
  public void after() {
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
        LOG.error("Unexpected error on deleting data.", e);
      }

      controlledActorClock.reset();
    }
  }

  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
  }

  public SpringBrokerBridge getSpringBrokerBridge() {
    return springBrokerBridge;
  }

  public Atomix getAtomix() {
    return broker.getAtomix();
  }

  public InetSocketAddress getGatewayAddress() {
    return brokerCfg.getGateway().getNetwork().toSocketAddress();
  }

  public Broker getBroker() {
    return this.broker;
  }

  public ControlledActorClock getClock() {
    return controlledActorClock;
  }

  public void restartBroker(final PartitionListener... listeners) {
    stopBroker();
    startBroker(listeners);
  }

  public void stopBroker() {
    if (broker != null) {
      broker.close();
      broker = null;
      System.gc();
    }
  }

  public void startBroker(final PartitionListener... listeners) {
    if (brokerCfg == null) {
      try (final InputStream configStream = configSupplier.get()) {
        if (configStream == null) {
          brokerCfg = new BrokerCfg();
        } else {
          brokerCfg =
              new TestConfigurationFactory()
                  .create(null, "zeebe.broker", configStream, BrokerCfg.class);
        }
        configureBroker(brokerCfg);
      } catch (final IOException e) {
        throw new RuntimeException("Unable to open configuration", e);
      }
    }

    broker =
        new Broker(
            brokerCfg,
            newTemporaryFolder.getAbsolutePath(),
            controlledActorClock,
            springBrokerBridge);

    final CountDownLatch latch = new CountDownLatch(brokerCfg.getCluster().getPartitionsCount());
    broker.addPartitionListener(new LeaderPartitionListener(latch));
    for (final PartitionListener listener : listeners) {
      broker.addPartitionListener(listener);
    }

    broker.start().join();

    try {
      latch.await(INSTALL_TIMEOUT, INSTALL_TIMEOUT_UNIT);
    } catch (final InterruptedException e) {
      LOG.info("Broker was not started in 15 seconds", e);
      Thread.currentThread().interrupt();
    }

    final EmbeddedGatewayService embeddedGatewayService = broker.getEmbeddedGatewayService();
    if (embeddedGatewayService != null) {
      final BrokerClient brokerClient = embeddedGatewayService.get().getBrokerClient();

      waitUntil(
          () -> {
            final BrokerTopologyManager topologyManager = brokerClient.getTopologyManager();
            final BrokerClusterState topology = topologyManager.getTopology();
            return topology != null && topology.getLeaderForPartition(1) >= 0;
          });
    }

    dataDirectories = broker.getBrokerContext().getBrokerConfiguration().getData().getDirectories();
  }

  public void configureBroker(final BrokerCfg brokerCfg) {
    // build-in exporters
    if (ENABLE_DEBUG_EXPORTER) {
      DEBUG_EXPORTER.accept(brokerCfg);
    }

    if (ENABLE_HTTP_EXPORTER) {
      HTTP_EXPORTER.accept(brokerCfg);
    }

    TEST_RECORDER.accept(brokerCfg);

    // custom configurators
    for (final Consumer<BrokerCfg> configurator : configurators) {
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
        final File stateDirectory = new File(partitionDirectory, STATE_DIRECTORY);
        if (stateDirectory.exists()) {
          deleteSnapshots(stateDirectory);
        }
      }
    }
  }

  private static class LeaderPartitionListener implements PartitionListener {

    private final CountDownLatch latch;

    LeaderPartitionListener(final CountDownLatch latch) {
      this.latch = latch;
    }

    @Override
    public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingLeader(
        final int partitionId, final long term, final LogStream logStream) {
      latch.countDown();
      return CompletableActorFuture.completed(null);
    }
  }
}
