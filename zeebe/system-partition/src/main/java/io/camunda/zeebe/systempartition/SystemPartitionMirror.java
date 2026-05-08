/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains newly committed records from the system-partition {@link LogStream}, decoding {@code
 * CLUSTER_CONFIGURATION} events and propagating them to the {@link SystemPartitionFacadeImpl}.
 *
 * <p>For every {@code CHANGE_PLAN_STAMPED}, {@code OPERATION_APPLIED}, or {@code CHANGE_COMPLETED}
 * event, the carried {@link ClusterConfiguration} is decoded and pushed into the facade's cached
 * snapshot via {@link SystemPartitionFacadeImpl#applyCommit(ClusterConfiguration)}. For every
 * cluster-configuration event (including {@code REJECT}), pending command futures are resolved
 * through {@link SystemPartitionFacadeImpl.PendingRequests#resolve(String,
 * ClusterConfigurationRecord)}.
 */
public final class SystemPartitionMirror extends Actor {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPartitionMirror.class);

  private final LogStream logStream;
  private final SystemPartitionFacadeImpl facade;
  private final String actorName;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  private LogStreamReader reader;

  public SystemPartitionMirror(final LogStream logStream, final SystemPartitionFacadeImpl facade) {
    this.logStream = logStream;
    this.facade = facade;
    actorName = "SystemPartitionMirror-" + logStream.getPartitionId();
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorStarted() {
    reader = logStream.newLogStreamReader();
    // Drain anything already present (replay on restart) so the cached snapshot is up to date.
    drain();
    logStream.registerRecordAvailableListener(this::onRecordAvailable);
  }

  @Override
  protected void onActorClosing() {
    if (reader != null) {
      reader.close();
      reader = null;
    }
  }

  /** Invoked by the {@link LogStream} on a different thread when new records become available. */
  private void onRecordAvailable() {
    actor.run(this::drain);
  }

  private void drain() {
    if (reader == null) {
      return;
    }
    while (reader.hasNext()) {
      final LoggedEvent event = reader.next();
      try {
        applyEvent(event);
      } catch (final Exception e) {
        LOG.warn("Failed to apply system-partition event at position {}", event.getPosition(), e);
      }
    }
  }

  private void applyEvent(final LoggedEvent event) {
    final RecordMetadata metadata = new RecordMetadata();
    event.readMetadata(metadata);
    if (metadata.getValueType() != ValueType.CLUSTER_CONFIGURATION) {
      return;
    }

    final ClusterConfigurationRecord record = new ClusterConfigurationRecord();
    event.readValue(record);

    final var intent = metadata.getIntent();
    if (intent instanceof ClusterConfigurationIntent ccIntent) {
      switch (ccIntent) {
        case CHANGE_PLAN_STAMPED, OPERATION_APPLIED, CHANGE_COMPLETED -> {
          final byte[] configBytes = record.getConfiguration();
          if (configBytes != null && configBytes.length > 0) {
            try {
              final ClusterConfiguration config =
                  serializer.decodeClusterTopology(configBytes, 0, configBytes.length);
              facade.applyCommit(config);
            } catch (final Exception e) {
              LOG.warn(
                  "Failed to decode ClusterConfiguration carried by {} event at position {}",
                  ccIntent,
                  event.getPosition(),
                  e);
            }
          }
        }
        case REJECT -> {
          // No cached-snapshot update; only the pending future needs to be resolved below.
        }
        default -> {
          // Commands are not appended into the log as committed events for our purposes; ignore.
          return;
        }
      }
      SystemPartitionFacadeImpl.PendingRequests.resolve(record.getRequestId(), record);
    }
  }
}
