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
  // TODO to be removed or used with OPT-7352
  //  static <R> R withPersistenceException(Supplier<R> supplier) throws PersistenceException {
  //    try {
  //      return supplier.get();
  //    } catch (Exception e) {
  //      throw new PersistenceException(e.getMessage(), e.getCause());
  //    }
  //  }
  //
  //  static <R> R withPersistenceException(Supplier<R> supplier, String errorMessage) throws
  // PersistenceException {
  //    try {
  //      return supplier.get();
  //    } catch (Exception e) {
  //      throw new PersistenceException(errorMessage, e);
  //    }
  //  }
  //
  //  static <R> R withOptimizeRuntimeException(ExceptionSupplier<R> supplier) throws Exception {
  //    try {
  //      return supplier.get();
  //    } catch (Exception e) {
  //      throw new Exception(e.getMessage(), e.getCause()); //TODO
  //    }
  //  }

  public static <R> R withIOException(ExceptionSupplier<R> supplier) throws IOException {
    try {
      return supplier.get();
    } catch (OpenSearchException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e.getMessage(), e.getCause());
    }
  }

  public static <R> R safe(
      ExceptionSupplier<R> supplier, Function<Exception, String> errorMessage, Logger log) {
    try {
      return supplier.get();
    } catch (Exception e) {
      final String message = errorMessage.apply(e);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
