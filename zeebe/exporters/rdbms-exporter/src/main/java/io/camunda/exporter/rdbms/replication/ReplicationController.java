/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.replication;

public interface ReplicationController extends AutoCloseable {

  void onFlush(long exporterPosition);

  /**
   * Returns {@code true} as long as the async replication is in sync, otherwise {@code false}.
   *
   * @return if the replication is in sync
   */
  default boolean isReplicationInSync() {
    return true;
  }
}
