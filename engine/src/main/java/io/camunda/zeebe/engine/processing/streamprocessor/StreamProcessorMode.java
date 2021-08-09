/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

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
  REPLAY
}
