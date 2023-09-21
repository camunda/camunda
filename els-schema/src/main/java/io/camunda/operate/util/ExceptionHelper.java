/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import org.opensearch.client.opensearch._types.OpenSearchException;

import java.io.IOException;
import java.util.function.Supplier;

public interface ExceptionHelper {
  static <R> R withPersistenceException(Supplier<R> supplier) throws PersistenceException {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage(), e.getCause());
    }
  }

  static <R> R withPersistenceException(Supplier<R> supplier, String errorMessage) throws PersistenceException {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new PersistenceException(errorMessage, e);
    }
  }

  static <R> R withOperateRuntimeException(ExceptionSupplier<R> supplier) {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new OperateRuntimeException(e.getMessage(), e.getCause());
    }
  }

  public static <R> R withIOException(ExceptionSupplier<R> supplier) throws IOException {
    try {
      return supplier.get();
    } catch (OpenSearchException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e.getMessage(), e.getCause());
    }
  }
}
