/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore;

import org.jspecify.annotations.Nullable;

/**
 * Base class for all exceptions thrown by {@link SecretStore} implementations.
 *
 * <p>Callers that want to handle any store error broadly can catch this type; callers that need to
 * distinguish specific failure modes should catch subtypes such as {@link
 * SecretStoreUnavailableException}.
 */
public abstract class SecretStoreException extends RuntimeException {

  protected SecretStoreException(final String message) {
    super(message);
  }

  protected SecretStoreException(final String message, final @Nullable Throwable cause) {
    super(message, cause);
  }
}
