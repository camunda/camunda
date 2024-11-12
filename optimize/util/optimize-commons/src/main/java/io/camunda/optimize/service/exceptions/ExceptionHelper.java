/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

import java.io.IOException;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;

public class ExceptionHelper {

  public static <R> R withIOException(final ExceptionSupplier<R> supplier) throws IOException {
    try {
      return supplier.get();
    } catch (final OpenSearchException e) {
      throw e;
    } catch (final Exception e) {
      throw new IOException(e.getMessage(), e.getCause());
    }
  }

  public static <R> R safe(
      final ExceptionSupplier<R> supplier,
      final Function<Exception, String> errorMessage,
      final Logger log) {
    try {
      return supplier.get();
    } catch (final Exception e) {
      final String message = errorMessage.apply(e);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
