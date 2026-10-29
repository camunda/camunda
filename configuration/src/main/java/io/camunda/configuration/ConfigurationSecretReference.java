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
import org.jspecify.annotations.Nullable;

/**
 * A {@code camunda.secrets.<name>} reference in a configuration value, for example a
 * secondary-storage password. The value stays the reference after binding; the component that
 * builds a client from it resolves the name from the internal-config store of its physical tenant,
 * so a value rotated in the store reaches it without a restart.
 *
 * <p>The whole value must be the reference. The name charset matches the one jobs use.
 */
@NullMarked
public final class ConfigurationSecretReference {

  public static final String PREFIX = "camunda.secrets.";

  private static final Pattern REFERENCE =
      Pattern.compile(Pattern.quote(PREFIX) + "([\\p{Alnum}_-]+)");

  private ConfigurationSecretReference() {}

  /** Returns the secret name if the value is a reference. */
  public static Optional<String> nameOf(final @Nullable String value) {
    if (value == null) {
      return Optional.empty();
    }
    final var matcher = REFERENCE.matcher(value.trim());
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  public static boolean isReference(final @Nullable String value) {
    return nameOf(value).isPresent();
  }
}
