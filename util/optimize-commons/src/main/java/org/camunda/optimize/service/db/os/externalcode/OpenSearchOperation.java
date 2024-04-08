/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.os.externalcode;

import static org.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.ExceptionSupplier;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.util.ObjectBuilderBase;

@AllArgsConstructor
@Slf4j
public class OpenSearchOperation {

  private static final String INDEX_FIELD = "index";
  protected OptimizeIndexNameService indexNameService;

  protected <T extends ObjectBuilderBase> T applyIndexPrefix(T request) {
    try {
      Field indexField = request.getClass().getDeclaredField(INDEX_FIELD);
      indexField.setAccessible(true);
      Object indexFieldContent = indexField.get(request);
      if (Objects.isNull(indexFieldContent)) return request;
      if (indexFieldContent instanceof final String currentIndex) {
        indexField.set(request, getIndexAliasFor(currentIndex));
      } else if (indexFieldContent instanceof List<?> currentIndexes) {
        List<String> fullyQualifiedIndexNames =
            currentIndexes.stream()
                .map(currentIndex -> getIndexAliasFor((String) currentIndex))
                .toList();
        indexField.set(request, fullyQualifiedIndexNames);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Cannot apply index prefix to request. It contains an " + "unsupported type: %s ",
                indexFieldContent.getClass().getName()));
      }
      return request;
    } catch (NoSuchFieldException e) {
      throw new OptimizeRuntimeException(
          "Could not apply prefix to index of type " + request.getClass());
    } catch (IllegalAccessException e) {
      throw new OptimizeRuntimeException(
          String.format("Failed to set value for the %s field.", INDEX_FIELD));
    }
  }

  protected List<String> applyIndexPrefix(String... indexes) {
    return Arrays.stream(indexes).map(this::getIndexAliasFor).toList();
  }

  protected String applyIndexPrefix(String index) {
    return getIndexAliasFor(index);
  }

  protected String getIndexAliasFor(String indexName) {
    if (StringUtils.isNotBlank(indexName) && indexName.startsWith(ZEEBE_RECORD_TEST_PREFIX)) {
      return indexName;
    }
    return indexNameService.getOptimizeIndexAliasForIndex(indexName);
  }

  protected String getIndex(ObjectBuilderBase builder) {
    // todo will be refactored in the OPT-7352
    try {
      Field indexField = builder.getClass().getDeclaredField(INDEX_FIELD);
      indexField.setAccessible(true);
      return indexField.get(builder).toString();
    } catch (Exception e) {
      log.error(
          String.format(
              "Failed to get the method %s from %s", INDEX_FIELD, builder.getClass().getName()));
      return "FAILED_INDEX";
    }
  }

  protected <R> R safe(ExceptionSupplier<R> supplier, Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (OpenSearchException e) {
      final String message =
          "An exception has occurred when trying to execute an OpenSearch operation";
      log.error(message, e);
      throw e;
    } catch (Exception e) {
      final String message = errorMessage.apply(e);
      log.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
