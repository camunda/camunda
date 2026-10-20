/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

/**
 * A log: a partition of a physical tenant of a cluster. Its text is {@code
 * {cluster}/{tenant}/{partition}}, as the service's notifications name it.
 *
 * @param cluster the cluster's id
 * @param tenant the physical tenant, {@code default} for the default one
 * @param partition the partition, 1 or more
 */
public record LogId(String cluster, String tenant, int partition) {

  public LogId {
    if (cluster.isEmpty() || tenant.isEmpty() || partition < 1) {
      throw new IllegalArgumentException(
          "Expected a cluster, a tenant and a partition of 1 or more, but got '%s', '%s' and %d"
              .formatted(cluster, tenant, partition));
    }
  }

  @Override
  public String toString() {
    return cluster + "/" + tenant + "/" + partition;
  }
}
