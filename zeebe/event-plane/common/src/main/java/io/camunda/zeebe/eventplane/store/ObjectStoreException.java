/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.io.IOException;
import java.net.URI;

/** A request to a remote store failed, and may succeed when it is repeated. */
public final class ObjectStoreException extends IOException {

  private final String operation;
  private final URI object;

  public ObjectStoreException(
      final String operation, final URI object, final RuntimeException cause) {
    super(
        "Expected to %s %s, but the request failed: %s".formatted(operation, object, cause), cause);
    this.operation = operation;
    this.object = object;
  }

  /** What was asked for, such as {@code put}. */
  public String operation() {
    return operation;
  }

  /** The object or prefix the request was about. */
  public URI object() {
    return object;
  }
}
