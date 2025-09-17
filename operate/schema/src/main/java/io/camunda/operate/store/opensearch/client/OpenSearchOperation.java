/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ExceptionSupplier;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.util.ObjectBuilderBase;
import org.slf4j.Logger;

public class OpenSearchOperation {
  public static final int QUERY_MAX_SIZE = 10000;
  protected Logger logger;

  public OpenSearchOperation(final Logger logger) {
    this.logger = logger;
  }

  protected String getIndex(final ObjectBuilderBase builder) {
    try {
      final Field indexField = builder.getClass().getDeclaredField("index");
      indexField.setAccessible(true);
      return indexField.get(builder).toString();
    } catch (final Exception e) {
      logger.error(String.format("Failed to get index from %s", builder.getClass().getName()));
      return "FAILED_INDEX";
    }
  }

  protected <R> R safe(
      final ExceptionSupplier<R> supplier, final Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (final OpenSearchException e) {
      throw e;
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    } catch (final Exception e) {
      final String message = errorMessage.apply(e);
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
