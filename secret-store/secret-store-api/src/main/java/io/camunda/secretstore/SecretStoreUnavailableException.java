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

/**
 * Thrown when a {@link SecretStore} operation cannot be completed because the backing store is
 * unavailable or unusable (e.g. missing file, network failure, malformed content).
 *
 * <p>Mirrors {@link SecretErrorCode#STORE_UNAVAILABLE}: {@link SecretStore#resolve} reports this
 * condition as a {@link SecretResolutionResult.Failed} result; {@link SecretStore#list} throws this
 * exception.
 */
@NullMarked
public final class SecretStoreUnavailableException extends RuntimeException {

  public SecretStoreUnavailableException(final String message) {
    super(message);
  }

  public SecretStoreUnavailableException(final String message, final @Nullable Throwable cause) {
    super(message, cause);
  }
}
