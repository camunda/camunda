/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static io.camunda.zeebe.it.logarchive.LogArchiveBucket.TIMELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.it.logarchive.StubEventPlane.TimelineKey;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * With an event plane configured, the cleaner deletes a replaced object only after the event plane
 * confirmed the merge that replaced it, so the event plane never lists an object that is gone.
 */
@ZeebeIntegration
final class LogArchiveMergeEventPlaneIT {

  private static final int PARTITION = 1;
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());
  private final String clusterId = "cluster-" + UUID.randomUUID();
  private final StubEventPlane eventPlane = new StubEventPlane();
  private final LogArchiveWorkload workload = new LogArchiveWorkload();

  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker =
      LogArchiveWorkload.enableMerging(
              LogArchiveBucket.enableLogArchive(
                  new TestStandaloneBroker()
                      .withUnifiedConfig(cfg -> LogArchiveBucket.configureStore(cfg, basePath))),
              3)
          .withProperty("zeebe.broker.data.backup.logArchive.eventPlane.url", eventPlane.url())
          .withProperty("zeebe.broker.data.backup.logArchive.eventPlane.clusterId", clusterId)
          .withProperty("zeebe.broker.data.backup.logArchive.eventPlane.requestTimeout", "PT2S");

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    try {
      broker.stop();
    } finally {
      bucket.close();
      eventPlane.close();
    }
  }

  @Test
  void shouldDeleteReplacedObjectsOnlyAfterTheEventPlaneConfirmedTheMerge() {
    // given -- an event plane that is down while the cluster archives and merges
    eventPlane.setDown(true);
    broker.start().awaitCompleteTopology();
    try (final var client = broker.newClientBuilder().build()) {
      final long process = workload.deployProcess(client);
      LogArchiveWorkload.createInstances(client, process, "load", 60);
    }
    await("objects were merged and replace small ones")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.mergedObjects()).hasSizeGreaterThanOrEqualTo(2);
              assertThat(LogArchiveWorkload.replacedObjects(view)).isNotEmpty();
            });

    // when -- the cleaner runs many times (interval and grace period are 1 and 2 seconds)
    // then -- nothing a merge replaced is deleted, and the event plane applied nothing
    await("replaced objects are kept while the event plane is down")
        .during(Duration.ofSeconds(6))
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(LogArchiveWorkload.replacedObjects(view))
                  .isNotEmpty()
                  .allMatch(o -> LogArchiveWorkload.exists(bucket.store(), view, o.key()));
              assertThat(eventPlane.entries(key())).isEmpty();
            });

    // when -- the event plane is up again and confirms the entries
    eventPlane.setDown(false);

    // then -- the replaced objects are deleted, and the event plane holds the merges that
    // replaced them
    await("replaced objects are deleted once the event plane confirmed their merges")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(LogArchiveWorkload.replacedObjects(view))
                  .isNotEmpty()
                  .noneMatch(o -> LogArchiveWorkload.exists(bucket.store(), view, o.key()));
            });
    final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
    final var confirmedMerges =
        eventPlane.entries(key()).stream()
            .filter(e -> "LOG_MERGED".equals(e.path("type").asText()))
            .toList();
    LogArchiveWorkload.replacedObjects(view)
        .forEach(
            replaced ->
                assertThat(confirmedMerges)
                    .as("the event plane applied the merge that replaced %s", replaced.key())
                    .anyMatch(
                        e ->
                            e.path("objects").path(0).path("firstIndex").asLong()
                                    <= replaced.firstIndex()
                                && e.path("objects").path(0).path("lastIndex").asLong()
                                    >= replaced.lastIndex()));
  }

  private TimelineKey key() {
    return new TimelineKey(clusterId, "default", PARTITION, TIMELINE);
  }
}
