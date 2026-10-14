/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.syspartition.ClusterIdentity.CreationTimeValue;
import io.camunda.zeebe.broker.system.syspartition.ClusterIdentity.IdValue;
import java.time.InstantSource;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Records the {@link ClusterIdentity} when the cluster is first bootstrapped. A leader is only
 * notified once it has applied every committed entry, so it puts each value only if none is
 * recorded yet, with one exception: a configured cluster id that differs from the recorded one
 * replaces it, with a warning, as the configuration is the operator's intent. The creation time is
 * never replaced. A put lost with its leadership is made again by the next leader.
 */
final class ClusterIdentityInitializer implements SystemPartitionListener {
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final SystemKeyValueReader reader;
  private final @Nullable String configuredClusterId;
  private final InstantSource clock;

  /**
   * @param reader the reader of the system partition's key-value store
   * @param configuredClusterId the cluster id to record, replacing a different recorded one, or
   *     null to keep the recorded one or record a random one
   * @param clock the clock giving the creation time
   */
  ClusterIdentityInitializer(
      final SystemKeyValueReader reader,
      final @Nullable String configuredClusterId,
      final InstantSource clock) {
    this.reader = reader;
    this.configuredClusterId = configuredClusterId;
    this.clock = clock;
  }

  @Override
  public void onBecomingLeader(final long term, final SystemKeyValueWriter writer) {
    final var recordedClusterId =
        reader.get(SystemPartitionKeyType.CLUSTER_ID, new IdValue()).map(IdValue::getId);
    final var hasConfiguredClusterId =
        configuredClusterId != null && !configuredClusterId.isBlank();
    if (recordedClusterId.isEmpty()) {
      putClusterId(
          writer, hasConfiguredClusterId ? configuredClusterId : UUID.randomUUID().toString());
    } else if (hasConfiguredClusterId && !recordedClusterId.get().equals(configuredClusterId)) {
      LOG.warn(
          "The configured cluster id '{}' differs from the cluster id '{}' recorded in the system"
              + " partition; replacing the recorded one with the configured one",
          configuredClusterId,
          recordedClusterId.get());
      putClusterId(writer, configuredClusterId);
    }
    if (reader
        .get(SystemPartitionKeyType.CLUSTER_CREATION_TIME, new CreationTimeValue())
        .isEmpty()) {
      writer
          .put(
              SystemPartitionKeyType.CLUSTER_CREATION_TIME,
              new CreationTimeValue().setEpochMillis(clock.millis()))
          .onComplete((position, error) -> logFailure("creation time", error));
    }
  }

  private static void putClusterId(final SystemKeyValueWriter writer, final String clusterId) {
    writer
        .put(SystemPartitionKeyType.CLUSTER_ID, new IdValue().setId(clusterId))
        .onComplete((position, error) -> logFailure("id", error));
  }

  private static void logFailure(final String value, final @Nullable Throwable error) {
    if (error != null) {
      LOG.debug("Failed to put the cluster {}; the next leader puts it again", value, error);
    }
  }
}
