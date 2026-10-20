/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.store;

import java.io.IOException;
import org.jspecify.annotations.Nullable;

/** An object that was expected in a store isn't there. */
public final class NoSuchObjectException extends IOException {

  private final String key;

  public NoSuchObjectException(final String key) {
    this(key, null);
  }

  public NoSuchObjectException(final String key, @Nullable final Throwable cause) {
    super("Expected object '%s' to exist, but it doesn't".formatted(key), cause);
    this.key = key;
  }

  /** The key of the object that isn't there. */
  public String key() {
    return key;
  }
}
