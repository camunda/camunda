/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public sealed interface SecretResolutionResult
    permits SecretResolutionResult.Resolved, SecretResolutionResult.Failed {

  record Resolved(String value) implements SecretResolutionResult {
    @Override
    public String toString() {
      return "Resolved[value=***]";
    }
  }

  record Failed(SecretErrorCode code, String message, @Nullable Throwable cause)
      implements SecretResolutionResult {}
}
