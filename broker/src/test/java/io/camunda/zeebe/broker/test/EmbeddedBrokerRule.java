/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.test;

import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.DEBUG_EXPORTER;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.TEST_RECORDER;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setCommandApiPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayApiPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setGatewayClusterPort;
import static io.camunda.zeebe.broker.test.EmbeddedBrokerConfigurator.setInternalApiPort;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.TestLoggers;
import io.camunda.zeebe.broker.clustering.ClusterServices;
import io.camunda.zeebe.broker.system.SystemContext;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.engine.state.QueryService;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.test.util.socket.SocketUtil;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.allocation.DirectBufferAllocator;
import io.netty.util.NetUtil;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.agrona.LangUtil;
import org.assertj.core.util.Files;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.Logger;

public final class EmbeddedBrokerRule extends ExternalResource {
  private static final int INSTALL_TIMEOUT = 5;
  private static final TimeUnit INSTALL_TIMEOUT_UNIT = TimeUnit.MINUTES;
  private static final Logger LOG = TestLoggers.TEST_LOGGER;
  private static final boolean ENABLE_DEBUG_EXPORTER = false;
  private static final String SNAPSHOTS_DIRECTORY = "snapshots";
  private static final String STATE_DIRECTORY = "state";
  private final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private final Consumer<BrokerCfg>[] configurators;
  private BrokerCfg brokerCfg;
  private Broker broker;
  private final ControlledActorClock controlledActorClock = new ControlledActorClock();
  private final SpringBrokerBridge springBrokerBridge = new SpringBrokerBridge();

  private long startTime;
  private AtomixCluster atomixCluster;
  private File newTemporaryFolder;
  private String dataDirectory;
  private SystemContext systemContext;

  @SafeVarargs
  public EmbeddedBrokerRule(final Consumer<BrokerCfg>... configurators) {
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

  public ClusterServices getClusterServices() {
    return broker.getBrokerContext().getClusterServices();
  }

  public AtomixCluster getAtomixCluster() {
    return atomixCluster;
  }

  public InetSocketAddress getGatewayAddress() {
    return brokerCfg.getGateway().getNetwork().toSocketAddress();
  }

  public Broker getBroker() {
    return broker;
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
      try {
        systemContext.getScheduler().stop().get();
      } catch (final InterruptedException | ExecutionException e) {
        LangUtil.rethrowUnchecked(e);
      }
      systemContext = null;
      System.gc();
    }
  }

  public void startBroker(final PartitionListener... listeners) {
    if (brokerCfg == null) {
      brokerCfg = new BrokerCfg(newTemporaryFolder.getAbsolutePath());
      configureBroker(brokerCfg);
    }

    final var scheduler = TestActorSchedulerFactory.ofBrokerConfig(brokerCfg, controlledActorClock);
    atomixCluster = TestClusterFactory.createAtomixCluster(brokerCfg);
    systemContext = new SystemContext(brokerCfg, scheduler, atomixCluster);
    scheduler.start();

    final var additionalListeners = new ArrayList<>(Arrays.asList(listeners));
    final CountDownLatch latch = new CountDownLatch(brokerCfg.getCluster().getPartitionsCount());
    additionalListeners.add(new LeaderPartitionListener(latch));

    broker = new Broker(systemContext, springBrokerBridge, additionalListeners);

    broker.start().join();

    try {
      latch.await(INSTALL_TIMEOUT, INSTALL_TIMEOUT_UNIT);
    } catch (final InterruptedException e) {
      LOG.info("Broker was not started in 15 seconds", e);
      Thread.currentThread().interrupt();
    }

    if (brokerCfg.getGateway().isEnable()) {
      try (final var client =
          ZeebeClient.newClientBuilder()
              .gatewayAddress(NetUtil.toSocketAddressString(getGatewayAddress()))
              .usePlaintext()
              .build()) {
        Awaitility.await("until we have a complete topology")
            .ignoreExceptions()
            .untilAsserted(
                () -> {
                  final var topology = client.newTopologyRequest().send().join();
                  TopologyAssert.assertThat(topology)
                      .isComplete(
                          brokerCfg.getCluster().getClusterSize(),
                          brokerCfg.getCluster().getPartitionsCount(),
                          brokerCfg.getCluster().getReplicationFactor());
                });
      }
    }

    dataDirectory = broker.getSystemContext().getBrokerConfiguration().getData().getDirectory();
  }

  public void configureBroker(final BrokerCfg brokerCfg) {
    // build-in exporters
    if (ENABLE_DEBUG_EXPORTER) {
      DEBUG_EXPORTER.accept(brokerCfg);
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
    final File directory = new File(dataDirectory);

    final File[] partitionDirectories = directory.listFiles((d, f) -> new File(d, f).isDirectory());
    if (partitionDirectories == null) {
      return;
    }

    for (final File partitionDirectory : partitionDirectories) {
      final File stateDirectory = new File(partitionDirectory, STATE_DIRECTORY);
      if (stateDirectory.exists()) {
        deleteSnapshots(stateDirectory);
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
        final int partitionId,
        final long term,
        final LogStream logStream,
        final QueryService queryService) {
      latch.countDown();
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
      return CompletableActorFuture.completed(null);
    }
  }
}
