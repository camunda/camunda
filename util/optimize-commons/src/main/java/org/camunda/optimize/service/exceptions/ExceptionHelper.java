/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.exceptions;

import org.opensearch.client.opensearch._types.OpenSearchException;

import java.io.IOException;

public interface ExceptionHelper {
  // TODO to be removed or used with OPT-7352
//  static <R> R withPersistenceException(Supplier<R> supplier) throws PersistenceException {
//    try {
//      return supplier.get();
//    } catch (Exception e) {
//      throw new PersistenceException(e.getMessage(), e.getCause());
//    }
//  }
//
//  static <R> R withPersistenceException(Supplier<R> supplier, String errorMessage) throws PersistenceException {
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

  static <R> R withIOException(ExceptionSupplier<R> supplier) throws IOException {
    try {
      return supplier.get();
    } catch (OpenSearchException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e.getMessage(), e.getCause());
    }
  }
}
