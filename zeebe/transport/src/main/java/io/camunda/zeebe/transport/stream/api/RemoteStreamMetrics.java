/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.stream.api;

import io.camunda.zeebe.transport.stream.impl.messages.ErrorCode;

public interface RemoteStreamMetrics {

  /** Invoked after a stream is successfully added to the registry */
  default void addStream() {}

  /** Invoked after a stream is removed from registry */
  default void removeStream() {}

  /** Invoked after a payload is successfully pushed to a stream */
  default void pushSucceeded() {}

  /** Invoked if pushing a payload to a stream failed */
  default void pushFailed() {}

  /**
   * Invoked when a push failed, once per remote attempt
   *
   * @param code the error code for the given attempt
   */
  default void pushTryFailed(final ErrorCode code) {}

  static RemoteStreamMetrics noop() {
    return new RemoteStreamMetrics() {};
  }
}
