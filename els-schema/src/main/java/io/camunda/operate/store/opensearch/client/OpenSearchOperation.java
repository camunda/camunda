/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.store.opensearch.client;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ExceptionSupplier;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.util.ObjectBuilderBase;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.function.Function;

public class OpenSearchOperation {
  public static final int QUERY_MAX_SIZE = 10000;
  protected Logger logger;

  public OpenSearchOperation(Logger logger) {
    this.logger = logger;
  }

  protected String getIndex(ObjectBuilderBase builder) {
    try {
      Field indexField = builder.getClass().getDeclaredField("index");
      indexField.setAccessible(true);
      return indexField.get(builder).toString();
    } catch (Exception e) {
      logger.error(String.format("Failed to get index from %s", builder.getClass().getName()));
      return "FAILED_INDEX";
    }
  }

  protected <R> R safe(ExceptionSupplier<R> supplier, Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (OpenSearchException e) {
      throw e;
    } catch (Exception e) {
      final String message = errorMessage.apply(e);
      logger.error(message, e);
      throw new OperateRuntimeException(message, e);
    }
  }
}
