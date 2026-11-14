/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.it.util.GrpcClientRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.unit.DataSize;

/**
 * A follower that runs out of disk space while installing a snapshot must be able to start again
 * once disk space is available.
 *
 * <p>Installing a snapshot resets the follower's log: all segments are deleted, then a new one is
 * created. If creating it fails because the disk is full, the reset is left half done. In
 * particular, the next segment the journal had already pre-created stays on disk with an empty
 * header, and the follower refuses to open its journal on the next start.
 *
 * <p>The follower falls behind because of a network partition, not because of its full disk: a
 * follower that falls behind because it cannot create segments has already used up its pre-created
 * segment, and recovers on restart. Brokers in production host many partitions on the same disk, so
 * a partition can fall behind for unrelated reasons while the disk fills up.
 *
 * <p>To get a real out-of-disk failure without filling the host's disk, the follower requires
 * slightly less free space for replication than is available when the test starts, and the test
 * then writes a ballast file that pushes the free space below that threshold.
 */
public class SnapshotInstallOutOfDiskTest {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotInstallOutOfDiskTest.class);
  private static final int FOLLOWER_ID = 1;
  private static final int PARTITION_ID = 1;
  private static final Duration SNAPSHOT_PERIOD = Duration.ofMinutes(5);
  private static final DataSize THRESHOLD_MARGIN = DataSize.ofMegabytes(256);
  private static final DataSize BALLAST_SIZE = DataSize.ofMegabytes(512);
  private static final Pattern SEGMENT_FILE = Pattern.compile(".+-partition-1-(\\d+)\\.log");

  private final ClusteringRule clusteringRule =
      new ClusteringRule(1, 3, 3, SnapshotInstallOutOfDiskTest::configureBroker);
  private final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public final RuleChain ruleChain =
      RuleChain.outerRule(Timeout.seconds(300)).around(clusteringRule).around(clientRule);

  private Path ballast;

  @After
  public void deleteBallast() throws IOException {
    if (ballast != null) {
      Files.deleteIfExists(ballast);
    }
  }

  @Test
  public void shouldStartFollowerThatRanOutOfDiskWhileInstallingSnapshot() throws IOException {
    // given -- a follower which has rolled over at least once, so its next segment is pre-created
    if (clusteringRule.getLeaderForPartition(PARTITION_ID).getNodeId() == FOLLOWER_ID) {
      clusteringRule.forceNewLeaderForPartition(0, PARTITION_ID);
    }
    final var follower = clusteringRule.getBroker(FOLLOWER_ID);
    final var segmentsDirectory = clusteringRule.getSegmentsDirectory(follower);
    clusteringRule.fillSegments(List.of(follower), 3);
    final long firstSegmentId =
        segmentIds(segmentsDirectory).stream().min(Long::compare).orElseThrow();
    LOG.info("Follower segments before ballast: {}", listFiles(segmentsDirectory));

    // the disk fills up, and the follower falls behind for an unrelated reason
    ballast = writeBallast(Path.of(follower.getConfig().getData().getDirectory(), "ballast"));
    clusteringRule.disconnect(follower);
    final var others = clusteringRule.getOtherBrokerObjects(FOLLOWER_ID);
    clusteringRule.fillSegments(others, 6);
    triggerSnapshots();
    others.forEach(clusteringRule::waitForSnapshotAtBroker);

    // when -- the leader sends a snapshot, and resetting the log for it fails for lack of space
    clusteringRule.connect(follower);
    Awaitility.await("until the follower reset its log for a snapshot")
        .atMost(Duration.ofMinutes(2))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> !segmentIds(segmentsDirectory).contains(firstSegmentId));
    LOG.info("Follower segments after failed install: {}", listFiles(segmentsDirectory));

    // disk space becomes available again, as when an operator extends the volume
    clusteringRule.stopBroker(FOLLOWER_ID);
    LOG.info("Follower segments after shutdown: {}", listFiles(segmentsDirectory));
    Files.delete(ballast);
    ballast = null;
    final var freeSpace =
        clusteringRule.getBrokerCfg(FOLLOWER_ID).getData().getDisk().getFreeSpace();
    freeSpace.setReplication(DataSize.ofMegabytes(64));
    freeSpace.setProcessing(DataSize.ofMegabytes(128));

    // then -- the follower starts its partition again and rejoins the replication group
    clusteringRule.startBroker(FOLLOWER_ID);
    Awaitility.await("until the restarted follower is healthy")
        .atMost(Duration.ofMinutes(1))
        .untilAsserted(() -> assertThat(clusteringRule.isBrokerHealthy(FOLLOWER_ID)).isTrue());
  }

  private void triggerSnapshots() {
    clientRule
        .getClient()
        .newDeployResourceCommand()
        .addProcessModel(Bpmn.createExecutableProcess("p").startEvent().done(), "p.bpmn")
        .send()
        .join();
    clusteringRule.getClock().addTime(SNAPSHOT_PERIOD);
  }

  private static void configureBroker(final BrokerCfg cfg) {
    final var data = cfg.getData();
    data.setSnapshotPeriod(SNAPSHOT_PERIOD);
    // segment size must fit the atomic identity-setup batch written at startup
    data.setLogSegmentSize(DataSize.ofKilobytes(128));
    data.setLogIndexDensity(1);
    cfg.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(32));

    if (cfg.getCluster().getNodeId() == FOLLOWER_ID) {
      final long threshold = usableTmpSpace() - THRESHOLD_MARGIN.toBytes();
      data.getDisk().getFreeSpace().setReplication(DataSize.ofBytes(threshold));
      data.getDisk().getFreeSpace().setProcessing(DataSize.ofBytes(threshold + 1));
    }
  }

  private static long usableTmpSpace() {
    try {
      return Files.getFileStore(Path.of(System.getProperty("java.io.tmpdir"))).getUsableSpace();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<Long> segmentIds(final Path directory) throws IOException {
    try (final Stream<Path> files = Files.list(directory)) {
      return files
          .map(p -> SEGMENT_FILE.matcher(p.getFileName().toString()))
          .filter(Matcher::matches)
          .map(m -> Long.parseLong(m.group(1)))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  private static List<String> listFiles(final Path directory) throws IOException {
    try (final Stream<Path> files = Files.list(directory)) {
      return files
          .filter(Files::isRegularFile)
          .map(p -> p.getFileName() + "(" + p.toFile().length() + ")")
          .sorted()
          .toList();
    }
  }

  /** Writes real zeros, as sparse files would not consume any disk space. */
  private static Path writeBallast(final Path path) throws IOException {
    final var chunk = ByteBuffer.allocateDirect((int) DataSize.ofMegabytes(1).toBytes());
    try (final var channel =
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
      for (long written = 0; written < BALLAST_SIZE.toBytes(); written += chunk.capacity()) {
        chunk.clear();
        while (chunk.hasRemaining()) {
          channel.write(chunk);
        }
      }
      channel.force(true);
    }
    return path;
  }
}
