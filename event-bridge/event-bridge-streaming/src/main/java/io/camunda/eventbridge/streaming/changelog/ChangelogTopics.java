/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

import io.camunda.eventbridge.client.EventBridgeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Idempotent provisioning of a shard's changelog topic (ADR 0009 Decision 1): one {@code COMPACT}
 * topic per stage/store, one partition per source partition, created once at startup — mirroring
 * how the analytics app provisions its facts topic today (see {@code AnalyticsProjectionStage}).
 */
public final class ChangelogTopics {

  private static final Logger LOG = LoggerFactory.getLogger(ChangelogTopics.class);
  private static final String CLEANUP_POLICY_COMPACT = "COMPACT";
  private static final int REPLICATION_FACTOR = 1;

  private ChangelogTopics() {}

  /**
   * Creates {@code topic} as a {@code COMPACT} topic with {@code partitionCount} partitions,
   * tolerating (and logging) a failure — the same already-exists tolerance the facts topic's own
   * provisioning uses, since topic creation is not itself idempotent at the client level.
   */
  public static void ensure(
      final EventBridgeClient client, final String topic, final int partitionCount) {
    try {
      client.createTopic(topic, partitionCount, REPLICATION_FACTOR, CLEANUP_POLICY_COMPACT).join();
    } catch (final RuntimeException e) {
      LOG.info(
          "Changelog topic {} already exists (or could not be created fresh): {}",
          topic,
          e.getMessage());
    }
  }
}
