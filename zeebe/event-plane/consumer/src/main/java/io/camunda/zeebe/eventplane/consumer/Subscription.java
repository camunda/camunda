/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

/**
 * Logs a group reads: every partition of a cluster's tenant. {@value #ANY} stands for every
 * cluster, or every tenant.
 *
 * @param cluster a cluster's id, or {@value #ANY}
 * @param tenant a physical tenant, or {@value #ANY}
 */
public record Subscription(String cluster, String tenant) {

  /** Every cluster, or every tenant. */
  public static final String ANY = "*";

  public Subscription {
    if (cluster.isEmpty() || tenant.isEmpty()) {
      throw new IllegalArgumentException(
          "Expected a cluster and a tenant, or '*', but got '%s' and '%s'"
              .formatted(cluster, tenant));
    }
  }

  /** Every tenant of every cluster. */
  public static Subscription all() {
    return new Subscription(ANY, ANY);
  }
}
