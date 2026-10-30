/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

package io.camunda.zeebe.engine.state.globallistener;

import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;

public interface MutableGlobalListenersState extends GlobalListenersState {
  void create(final GlobalListenerRecord record);

  void update(final GlobalListenerRecord record);

  void delete(final GlobalListenerRecord record);

  /**
   * Change the key of the current global listeners configuration. This key is used to link the
   * current configuration to the stored versions of the configuration, e.g. when pinning a
   * configuration version to a user task. Every time the configuration is changed (e.g. by
   * creating/updating/deleting a global listener), the configuration key should be updated to a new
   * value.
   */
  void updateConfigKey(final long key);

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
