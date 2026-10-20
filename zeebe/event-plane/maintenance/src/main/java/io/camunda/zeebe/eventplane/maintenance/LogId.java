/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.segment.Identity;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import java.util.UUID;

/** A log: a partition of a physical tenant of a cluster, by the plain names, not the encoded. */
record LogId(String cluster, String tenant, int partition) {

  /** The log's directory. */
  String prefix() {
    return ObjectNames.logPrefix(cluster, tenant, partition);
  }

  /** The directory of the segments that hold one history's journal. */
  String segmentsPrefix(final UUID history) {
    return ObjectNames.segmentsPrefix(cluster, tenant, partition, history);
  }

  /** The directory of the log's retention markers. */
  String retentionPrefix() {
    return ObjectNames.retentionPrefix(cluster, tenant, partition);
  }

  /** What the metadata of each of the log's segments names. */
  Identity identity() {
    return new Identity(cluster, tenant, partition);
  }

  @Override
  public String toString() {
    return "%s/%s/%d".formatted(cluster, tenant, partition);
  }
}
