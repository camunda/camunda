/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.history;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** The ids of histories that nobody has to make up. */
public final class HistoryIds {

  private HistoryIds() {}

  /**
   * The first history of a physical tenant: the name-based UUID, as {@link UUID#nameUUIDFromBytes},
   * of {@code event-plane/initial/{clusterKey}/{tenant}}. Every partition works it out without
   * talking to another.
   *
   * @param clusterKey the cluster configuration's generated cluster id, or the cluster's name
   * @param tenant the physical tenant: the partition group's name, {@code default} for the default
   */
  public static UUID initial(final String clusterKey, final String tenant) {
    return UUID.nameUUIDFromBytes(
        "event-plane/initial/%s/%s".formatted(clusterKey, tenant).getBytes(StandardCharsets.UTF_8));
  }
}
