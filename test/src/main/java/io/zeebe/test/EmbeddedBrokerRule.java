/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import static io.zeebe.test.util.TestUtil.waitUntil;

import io.zeebe.broker.Broker;
import io.zeebe.broker.SpringBrokerBridge;
import io.zeebe.broker.system.EmbeddedGatewayService;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.broker.system.configuration.ExporterCfg;
import io.zeebe.broker.system.configuration.NetworkCfg;
import io.zeebe.gateway.impl.broker.BrokerClient;
import io.zeebe.gateway.impl.broker.cluster.BrokerClusterState;
import io.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.zeebe.test.util.TestConfigurationFactory;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.test.util.socket.SocketUtil;
import io.zeebe.util.FileUtil;
import io.zeebe.util.ZbLogger;
import io.zeebe.util.allocation.DirectBufferAllocator;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.assertj.core.util.Files;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public class EmbeddedBrokerRule extends ExternalResource {

  public static final String DEFAULT_CONFIG_FILE = "zeebe.test.cfg.yaml";
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
  protected Broker broker;
  protected long startTime;
  private final int timeout;
  private final File newTemporaryFolder;
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
        DEFAULT_TIMEOUT,
        configurators);
  }

  @SafeVarargs
  public EmbeddedBrokerRule(
      final Supplier<InputStream> configSupplier,
      final int timeout,
      final Consumer<BrokerCfg>... configurators) {
    this.configSupplier = configSupplier;
    this.configurators = configurators;
    this.timeout = timeout;

    newTemporaryFolder = Files.newTemporaryFolder();
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
    brokerCfg.getGateway().getNetwork().setPort(SocketUtil.getNextAddress().getPort());
    network.getCommandApi().setPort(SocketUtil.getNextAddress().getPort());
    network.getInternalApi().setPort(SocketUtil.getNextAddress().getPort());
    network.getMonitoringApi().setPort(SocketUtil.getNextAddress().getPort());
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
        LOG.error("Unexpected error on deleting data.", e);
      }
    }
  }

  public BrokerCfg getBrokerCfg() {
    return brokerCfg;
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
    broker =
        new Broker(
            brokerCfg,
            newTemporaryFolder.getAbsolutePath(),
            controlledActorClock,
            new SpringBrokerBridge());
    broker.start().join();

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
    final ExporterCfg exporterCfg = new ExporterCfg();
    exporterCfg.setClassName(RecordingExporter.class.getName());
    brokerCfg.getExporters().put(TEST_RECORD_EXPORTER_ID, exporterCfg);

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
}
