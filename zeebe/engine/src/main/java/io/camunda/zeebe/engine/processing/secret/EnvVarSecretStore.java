/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.secret;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * {@link SecretStore} backed by process environment variables.
 *
 * <p>The bare secret name is used as the environment variable name verbatim (no prefix). For
 * safety, only identifier-shaped names (matching {@link #VALID_NAME}) are looked up; any other name
 * resolves to {@link Optional#empty()} to avoid shell or path injection via the FEEL expression
 * text.
 */
public final class EnvVarSecretStore implements SecretStore {

  /** Identifier-style names only — same shape as POSIX-portable env var names. */
  public static final Pattern VALID_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

  private final Function<String, String> lookup;

  public EnvVarSecretStore() {
    this(System::getenv);
  }

  /** Visible for testing — allows injecting an in-memory env-var source. */
  EnvVarSecretStore(final Function<String, String> lookup) {
    this.lookup = lookup;
  }

  @Override
  public Optional<String> resolve(final String secretName) {
    if (secretName == null || !VALID_NAME.matcher(secretName).matches()) {
      return Optional.empty();
    }
    return Optional.ofNullable(lookup.apply(secretName));
  }
}
