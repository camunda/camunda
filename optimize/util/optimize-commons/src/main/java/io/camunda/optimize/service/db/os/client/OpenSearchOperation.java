/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.client;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_RECORD_TEST_PREFIX;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.ExceptionSupplier;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.util.ObjectBuilderBase;
import org.slf4j.Logger;

public class OpenSearchOperation {

  private static final String INDEX_FIELD = "index";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OpenSearchOperation.class);
  protected OptimizeIndexNameService indexNameService;

  public OpenSearchOperation(final OptimizeIndexNameService indexNameService) {
    this.indexNameService = indexNameService;
  }

  protected <T extends ObjectBuilderBase> T applyIndexPrefix(final T request) {
    try {
      final Field indexField = request.getClass().getDeclaredField(INDEX_FIELD);
      indexField.setAccessible(true);
      final Object indexFieldContent = indexField.get(request);
      if (Objects.isNull(indexFieldContent)) {
        return request;
      }
      if (indexFieldContent instanceof final String currentIndex) {
        indexField.set(request, getIndexAliasFor(currentIndex));
      } else if (indexFieldContent instanceof final List<?> currentIndexes) {
        final List<String> fullyQualifiedIndexNames =
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
    } catch (final NoSuchFieldException e) {
      throw new OptimizeRuntimeException(
          "Could not apply prefix to index of type " + request.getClass());
    } catch (final IllegalAccessException e) {
      throw new OptimizeRuntimeException(
          String.format("Failed to set value for the %s field.", INDEX_FIELD));
    }
  }

  protected List<String> applyIndexPrefix(final String... indexes) {
    return Arrays.stream(indexes).map(this::getIndexAliasFor).toList();
  }

  protected String applyIndexPrefix(final String index) {
    return getIndexAliasFor(index);
  }

  protected String getIndexAliasFor(final String indexName) {
    if (StringUtils.isNotBlank(indexName) && indexName.startsWith(ZEEBE_RECORD_TEST_PREFIX)) {
      return indexName;
    }
    return indexNameService.getOptimizeIndexAliasForIndex(indexName);
  }

  protected String getIndex(final ObjectBuilderBase builder) {
    try {
      final Field indexField = builder.getClass().getDeclaredField(INDEX_FIELD);
      indexField.setAccessible(true);
      return indexField.get(builder).toString();
    } catch (final Exception e) {
      LOG.error(
          String.format(
              "Failed to get the method %s from %s", INDEX_FIELD, builder.getClass().getName()));
      return "FAILED_INDEX";
    }
  }

  protected <R> R safe(
      final ExceptionSupplier<R> supplier, final Function<Exception, String> errorMessage) {
    try {
      return supplier.get();
    } catch (final OpenSearchException e) {
      // OpenSearch exceptions shall only get re-thrown since they will be logged elsewhere
      throw e;
    } catch (final Exception e) {
      final String message = errorMessage.apply(e);
      LOG.error(message, e);
      throw new OptimizeRuntimeException(message, e);
    }
  }
}
