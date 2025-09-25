/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.intent;

/**
 * This intent is used to track user-triggered requests that may complete asynchronously. Such
 * requests can be temporarily deferred during processing – for example, due to the need to handle
 * user task listeners.
 */
public enum AsyncRequestEngineIntent implements EngineIntent {

  /**
   * Emitted when a request is received and its context must be preserved for later use, including
   * writing follow-up events and responses.
   */
  RECEIVED((short) 0),

  /**
   * Emitted once the request has been fully processed and the preserved context is no longer
   * needed. Acts as a cleanup signal.
   */
  PROCESSED((short) 1);

  private final short value;

  AsyncRequestEngineIntent(final short value) {
    this.value = value;
  }

  public static EngineIntent from(final short value) {
    switch (value) {
      case 0:
        return RECEIVED;
      case 1:
        return PROCESSED;
      default:
        return UNKNOWN;
    }
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return true;
  }
}
