/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Optional;
import java.util.regex.Pattern;
import org.jspecify.annotations.NullMarked;

/**
 * A configuration value that refers to a configuration secret, written as {@code secret:<name>},
 * where the name follows the same rules as in processes.
 */
@NullMarked
public record ConfigurationSecretReference(String name) {

  public static final String PREFIX = "secret:";

  private static final Pattern NAME = Pattern.compile("[\\p{Alnum}_-]+");

  public ConfigurationSecretReference {
    if (!NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("Expected a secret name of " + NAME + ", got " + name);
    }
  }

  /** Whether the value is written as a reference, which {@link #parse} may still reject. */
  public static boolean isReference(final String value) {
    return value.startsWith(PREFIX);
  }

  /** Returns the reference that the value is, or nothing if it isn't one or is malformed. */
  public static Optional<ConfigurationSecretReference> parse(final String value) {
    if (!isReference(value) || !NAME.matcher(value.substring(PREFIX.length())).matches()) {
      return Optional.empty();
    }
    return Optional.of(new ConfigurationSecretReference(value.substring(PREFIX.length())));
  }

  @Override
  public String toString() {
    return PREFIX + name;
  }
}
