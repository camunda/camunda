/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clusterversion;

/**
 * Notified when the Engine Capability Version (ECV) changes or is established on this partition.
 *
 * <p>Used by the broker's command-API admission layer to keep its cached active ECV in sync. Fired
 * twice in the normal lifecycle:
 *
 * <ul>
 *   <li><b>Seed</b>: once on stream-processor recovery, with the active ECV reconstructed from the
 *       log.
 *   <li><b>Push</b>: after every {@code ClusterVersionIntent.APPLIED} event, with the new active
 *       ECV.
 * </ul>
 *
 * <p>The callback runs on the stream-processor actor — implementations must not block.
 */
@FunctionalInterface
public interface ClusterVersionUpdateListener {

  ClusterVersionUpdateListener NOOP = (line, ordinal) -> {};

  void onClusterVersionUpdate(int line, int ordinal);
}
