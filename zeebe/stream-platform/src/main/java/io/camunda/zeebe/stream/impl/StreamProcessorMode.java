/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

public enum StreamProcessorMode {
  /**
   * When in PROCESSING mode, stream processor first replays existing events, and then switch to
   * processing commands. This is the mode used by the leader.
   */
  PROCESSING,

  /**
   * When in REPLAY mode, all events are replayed and commands are never processed. This is the mode
   * used in followers.
   */
  REPLAY;

  public static StreamProcessorMode fromRole(final boolean isLeader) {
    return isLeader ? StreamProcessorMode.PROCESSING : StreamProcessorMode.REPLAY;
  }
}
