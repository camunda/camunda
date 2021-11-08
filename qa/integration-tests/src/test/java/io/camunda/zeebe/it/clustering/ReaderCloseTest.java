/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.it.util.GrpcClientRule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.springframework.util.unit.DataSize;

public class ReaderCloseTest {

  public final Timeout testTimeout = Timeout.seconds(120);
  public final ClusteringRule clusteringRule =
      new ClusteringRule(
          1,
          3,
          3,
          config -> {
            config.getNetwork().setMaxMessageSize(DataSize.ofKilobytes(8));
            config.getData().setLogSegmentSize(DataSize.ofKilobytes(8));
            // no exporters so that segments are immediately compacted after a snapshot.
            config.setExporters(Map.of());
          });
  public final GrpcClientRule clientRule = new GrpcClientRule(clusteringRule);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(testTimeout).around(clusteringRule).around(clientRule);

  // Regression test for https://github.com/camunda-cloud/zeebe/issues/7767
  @Test
  public void shouldDeleteCompactedSegmentsFiles() throws IOException {
    // given
    fillSegments();

    // when
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    for (final Broker broker : clusteringRule.getBrokers()) {
      assertThatFilesOfDeletedSegmentsDoesNotExist(broker);
    }
  }

  // Regression test for https://github.com/camunda-cloud/zeebe/issues/7767
  @Test
  public void shouldDeleteCompactedSegmentsFilesAfterLeaderChange() throws IOException {
    // given
    fillSegments();
    final var leaderId = clusteringRule.getLeaderForPartition(1).getNodeId();
    final var followerId =
        clusteringRule.getOtherBrokerObjects(leaderId).stream()
            .findAny()
            .orElseThrow()
            .getConfig()
            .getCluster()
            .getNodeId();
    clusteringRule.forceClusterToHaveNewLeader(followerId);

    // when
    clusteringRule.triggerAndWaitForSnapshots();

    // then
    for (final Broker broker : clusteringRule.getBrokers()) {
      assertThatFilesOfDeletedSegmentsDoesNotExist(broker);
    }
  }

  private void assertThatFilesOfDeletedSegmentsDoesNotExist(final Broker leader)
      throws IOException {
    final var segmentDirectory = clusteringRule.getSegmentsDirectory(leader);
    try (final var stream =
        Files.newDirectoryStream(segmentDirectory, path -> !path.toFile().isDirectory())) {
      stream.forEach(
          path ->
              assertThat(isEitherLogOrRaftMetaFiles(path))
                  .as(
                      "The files in the segment directory should be either valid log segments or raft config and metadata. %s",
                      path)
                  .isTrue());
    }
  }

  private boolean isEitherLogOrRaftMetaFiles(final Path path) {
    final var filename = path.getFileName().toString();
    return filename.endsWith(".log")
        || filename.endsWith(".conf")
        || filename.endsWith(".meta")
        || filename.endsWith(".lock");
  }

  public void fillSegments() {
    runUntilSegmentsFilled(
        clusteringRule.getBrokers(),
        2,
        () ->
            clientRule
                .getClient()
                .newPublishMessageCommand()
                .messageName("msg")
                .correlationKey("key")
                .send()
                .join());
  }

  public void runUntilSegmentsFilled(
      final Collection<Broker> brokers, final int segmentCount, final Runnable runnable) {
    while (brokers.stream().map(this::getSegmentsCount).allMatch(count -> count <= segmentCount)) {
      runnable.run();
    }
    runnable.run();
  }

  public int getSegmentsCount(final Broker broker) {
    return getSegments(broker).size();
  }

  Collection<Path> getSegments(final Broker broker) {
    try {
      return Files.list(clusteringRule.getSegmentsDirectory(broker))
          .filter(path -> path.toString().endsWith(".log"))
          .collect(Collectors.toList());
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
