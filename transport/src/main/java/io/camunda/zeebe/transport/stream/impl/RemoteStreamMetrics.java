/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

public interface RemoteStreamMetrics {

  /** Invoked after a stream is successfully added to the registry */
  default void addStream() {}

  /** Invoked after a stream is removed from registry */
  default void removeStream() {}

  /** Invoked after a payload is successfully pushed to a stream */
  default void pushSucceeded() {}

  /** Invoked if pushing a payload to a stream failed */
  default void pushFailed() {}

  static RemoteStreamMetrics noop() {
    return new RemoteStreamMetrics() {};
  }
}
