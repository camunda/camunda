/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.externalcode;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.ExceptionSupplier;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.util.ObjectBuilderBase;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

@AllArgsConstructor
@Slf4j
public class OpenSearchOperation {
  private static final String INDEX_METHOD = "index";
  protected OptimizeIndexNameService indexNameService;


  protected  <T extends ObjectBuilderBase> T applyIndexPrefix(T request) {
    Field indexField;
    try {
      indexField = request.getClass().getDeclaredField(INDEX_METHOD);
      indexField.setAccessible(true);
      String currentIndex = (String) indexField.get(request);
      String fullyQualifiedIndexName = indexNameService.getOptimizeIndexAliasForIndex(currentIndex);
      Method setIndexMethod = request.getClass().getMethod(INDEX_METHOD, String.class);
      setIndexMethod.invoke(request, fullyQualifiedIndexName);
      return request;
    } catch (NoSuchFieldException e) {
      throw new OptimizeRuntimeException("Could not apply prefix to index of type " + request.getClass());
    } catch (NoSuchMethodException e) {
      throw new OptimizeRuntimeException(String.format("The object does not have an %s() method.", INDEX_METHOD));
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw new OptimizeRuntimeException(String.format("Failed to invoke the %s() method.", INDEX_METHOD));
    }

  }

  protected String getIndexAliasFor(String indexName) {
    return indexNameService.getOptimizeIndexAliasForIndex(indexName);
  }

  protected String getIndex(ObjectBuilderBase builder) {
    try {
      Field indexField = builder.getClass().getDeclaredField(INDEX_METHOD);
      indexField.setAccessible(true);
      return indexField.get(builder).toString();
    } catch (Exception e) {
      log.error(String.format("Failed to get the method %s from %s", INDEX_METHOD, builder.getClass().getName()));
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
      log.error(message, e);
      // TODO throw Optimize Exception - fix with OPT-7352
      return null;
    }
  }
}
