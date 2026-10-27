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
import io.camunda.zeebe.protocol.record.value.GlobalListenerType;

public interface GlobalListenersState {

  /** Gets the current global listeners configuration. */
  GlobalListenerBatchRecord getCurrentConfig();

  /**
   * Gets the current global listeners configuration key.
   *
   * @return the current configuration key, or {@code null} if no configuration is set
   */
  Long getCurrentConfigKey();

  /**
   * Gets a stored copy of a global listeners configuration, possibly different from the current
   * one, given a version key.
   *
   * @param versionKey the version key of the desired configuration
   * @return the global listeners configuration for the given version key, or {@code null} if not
   *     found
   */
  GlobalListenerBatchRecord getVersionedConfig(final long versionKey);

  /**
   * Checks whether a global listeners configuration version is stored.
   *
   * @param versionKey the version key to check
   * @return {@code true} if the configuration version is stored, {@code false} otherwise
   */
  boolean isConfigurationVersionStored(final long versionKey);

  /**
   * Checks whether a global listeners configuration version is pinned by any element. In
   * particular, the configuration could be pinned by a user task in order to always reference the
   * same list of global user task listeners.
   *
   * @param versionKey the version key to check
   * @return {@code true} if the configuration version is pinned, {@code false} otherwise
   */
  boolean isConfigurationVersionPinned(final long versionKey);

  /**
   * Gets the global listener identified by the provided type and id, if it exists.
   *
   * @param listenerType the type of listener being retrieved
   * @param id the identifier of the listener
   * @return the global listener for the given listener type and id, or {@code null} if not found
   */
  GlobalListenerRecord getGlobalListener(final GlobalListenerType listenerType, final String id);
}
