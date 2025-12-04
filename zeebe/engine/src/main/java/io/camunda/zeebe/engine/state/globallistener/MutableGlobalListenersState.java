/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;

public interface MutableGlobalListenersState extends GlobalListenersState {
  /**
   * Replace the current global listeners configuration with the given one.
   *
   * @param record the new global listeners configuration
   */
  void updateCurrentConfiguration(final GlobalListenerBatchRecord record);

  /**
   * Store a copy of the given global listeners configuration and return its version key.
   *
   * @param record the global listeners configuration to store
   * @return the version key assigned to the stored configuration
   */
  long storeConfigurationVersion(final GlobalListenerBatchRecord record);

  /**
   * Delete the stored global listeners configuration version identified by the given version key.
   *
   * @param versionKey the version key of the configuration to delete
   */
  void deleteConfigurationVersion(final long versionKey);

  /**
   * Pin the global listeners configuration version identified by the given version key to the given
   * pinning element (e.g., a user task).
   *
   * @param versionKey the version key of the configuration to pin
   * @param pinningElementKey the key of the element to which the configuration is pinned
   */
  void pinConfiguration(final long versionKey, final long pinningElementKey);

  /**
   * Unpin the global listeners configuration version identified by the given version key from the
   * given pinning element (e.g., a user task).
   *
   * @param versionKey the version key of the configuration to unpin
   * @param pinningElementKey the key of the element from which the configuration is unpinned
   */
  void unpinConfiguration(final long versionKey, final long pinningElementKey);
}
