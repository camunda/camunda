/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import java.io.IOException;
import java.util.function.Supplier;
import org.opensearch.client.opensearch._types.OpenSearchException;

public interface ExceptionHelper {
  static <R> R withPersistenceException(Supplier<R> supplier) throws PersistenceException {
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new PersistenceException(e.getMessage(), e.getCause());
    }
  }

  static <R> R withPersistenceException(Supplier<R> supplier, String errorMessage)
      throws PersistenceException {
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
