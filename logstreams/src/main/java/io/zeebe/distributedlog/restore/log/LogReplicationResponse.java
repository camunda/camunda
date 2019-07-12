/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log;

public interface LogReplicationResponse {

  /** @return position of the last event to be serialized */
  long getToPosition();

  /**
   * If the response {@link #getToPosition()} is lower than what was requested (e.g. send buffer was
   * filled early), then the server can indicate here whether it has more events available in the
   * range that was requested.
   *
   * @return if true, indicates the server has more events available, false otherwise
   */
  boolean hasMoreAvailable();

  /**
   * @return a block of complete, serialized {@link io.zeebe.logstreams.log.LoggedEvent}; can be
   *     null or empty
   */
  byte[] getSerializedEvents();

  /** @return true if the response can be processed, false otherwise */
  default boolean isValid() {
    return getToPosition() > 0
        && (getSerializedEvents() != null && getSerializedEvents().length > 0);
  }
}
