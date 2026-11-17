/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import java.util.List;

/**
 * Thrown when an endpoint supports only some secondary storages and the request's physical tenant
 * is configured with a different one. HTTP 403 — contrast with {@link
 * SecondaryStorageUnavailableException}, which covers no secondary storage at all, and {@link
 * SecondaryStorageDegradedException}, which is HTTP 503 for a configured but currently-degraded
 * physical tenant.
 *
 * <p>The message names both sides so an operator on, say, an RDBMS tenant learns why the endpoint
 * is refused rather than reading a bare 403 as a permissions problem.
 */
public class SecondaryStorageTypeNotSupportedException extends ServiceException {

  public static final String UNSUPPORTED_SECONDARY_STORAGE_TYPE_MESSAGE =
      "This endpoint requires one of the following secondary storages: %s, but the configured "
          + "secondary storage is '%s'. Secondary storage is configured using the "
          + "'camunda.data.secondary-storage.type' property.";

  public SecondaryStorageTypeNotSupportedException(
      final List<String> supportedTypes, final String configuredType) {
    super(
        UNSUPPORTED_SECONDARY_STORAGE_TYPE_MESSAGE.formatted(
            String.join(", ", supportedTypes), configuredType),
        Status.FORBIDDEN);
  }
}
