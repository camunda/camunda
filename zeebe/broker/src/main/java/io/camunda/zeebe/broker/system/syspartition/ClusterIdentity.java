/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Instant;
import java.util.Optional;

/**
 * The identity of the cluster, recorded in the system partition when the cluster is first
 * bootstrapped: its {@link SystemPartitionKeyType#CLUSTER_ID id}, replaced only when a different
 * one is configured, and its {@link SystemPartitionKeyType#CLUSTER_CREATION_TIME creation time},
 * never changed afterwards.
 *
 * @param clusterId the configured cluster id, or a random one if none is configured
 * @param creationTime when the cluster was first bootstrapped, by the clock of its first leader
 */
public record ClusterIdentity(String clusterId, Instant creationTime) {

  /** Reads the identity from the given reader, or empty until both values are replicated to it. */
  public static Optional<ClusterIdentity> read(final SystemKeyValueReader reader) {
    final var id = reader.get(SystemPartitionKeyType.CLUSTER_ID, new IdValue());
    final var creationTime =
        reader.get(SystemPartitionKeyType.CLUSTER_CREATION_TIME, new CreationTimeValue());
    if (id.isEmpty() || creationTime.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new ClusterIdentity(
            id.get().getId(), Instant.ofEpochMilli(creationTime.get().getEpochMillis())));
  }

  static final class IdValue extends UnpackedObject {
    private final StringProperty idProp = new StringProperty("id", "");

    IdValue() {
      super(1);
      declareProperty(idProp);
    }

    String getId() {
      return BufferUtil.bufferAsString(idProp.getValue());
    }

    IdValue setId(final String id) {
      idProp.setValue(id);
      return this;
    }
  }

  static final class CreationTimeValue extends UnpackedObject {
    private final LongProperty epochMillisProp = new LongProperty("epochMillis", -1L);

    CreationTimeValue() {
      super(1);
      declareProperty(epochMillisProp);
    }

    long getEpochMillis() {
      return epochMillisProp.getValue();
    }

    CreationTimeValue setEpochMillis(final long epochMillis) {
      epochMillisProp.setValue(epochMillis);
      return this;
    }
  }
}
