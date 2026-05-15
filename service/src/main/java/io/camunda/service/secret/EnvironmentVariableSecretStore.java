/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.secret;

import java.util.Optional;
import java.util.function.Function;

/** Resolves secrets from process environment variables. */
public final class EnvironmentVariableSecretStore implements SecretStore {

  public static final String ID = "env";

  private final Function<String, String> envLookup;

  public EnvironmentVariableSecretStore() {
    this(System::getenv);
  }

  EnvironmentVariableSecretStore(final Function<String, String> envLookup) {
    this.envLookup = envLookup;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Optional<String> resolve(final String secretName) {
    return Optional.ofNullable(envLookup.apply(secretName));
  }
}
