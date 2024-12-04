/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db;

import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ImportRequestDto.Fields;
import io.camunda.optimize.dto.optimize.RequestType;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.ScriptData;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.DatabaseType;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeExecutor;
import net.jodah.failsafe.RetryPolicy;
import org.apache.tika.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DatabaseClient implements ConfigurationReloadable {

  protected static final String NESTED_DOC_LIMIT_MESSAGE =
      "The number of nested documents has exceeded the allowed limit of";
  private static final int DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY = 30;
  private static final Logger LOG = LoggerFactory.getLogger(DatabaseClient.class);
  protected OptimizeIndexNameService indexNameService;

  private int snapshotInProgressRetryDelaySeconds = DEFAULT_SNAPSHOT_IN_PROGRESS_RETRY_DELAY;

  /**
   * Get all the aliases for the indexes matching the indexNamePattern
   *
   * @param indexNamePattern Pattern for the name of an index, may contain wildcards
   * @return A Map where the keys are the name of the matching indexes and the value is a set
   *     containing the aliases for the respective index. This map can have multiple keys because
   *     indexNamePattern may contain wildcards
   */
  public abstract Map<String, Set<String>> getAliasesForIndexPattern(final String indexNamePattern)
      throws IOException;

  public abstract Set<String> getAllIndicesForAlias(final String aliasName) throws IOException;

  public abstract boolean exists(final String indexName) throws IOException;

  public abstract boolean triggerRollover(final String indexAliasName, final int maxIndexSizeGB);

  public abstract void deleteIndex(final String indexAlias);

  public abstract long countWithoutPrefix(final String unprefixedIndex) throws IOException;

  public abstract void refresh(final String indexPattern);

  public abstract List<String> getAllIndexNames() throws IOException;

  public abstract List<String> addPrefixesToIndices(String... indexes);

  public abstract String getDatabaseVersion() throws IOException;

  public abstract void setDefaultRequestOptions();

  public abstract void update(
      final String indexName, final String entityId, final ScriptData script);

  public abstract void executeImportRequestsAsBulk(
      final String bulkRequestName,
      final List<ImportRequestDto> importRequestDtos,
      final Boolean retryFailedRequestsOnNestedDocLimit);

  public abstract Set<String> performSearchDefinitionQuery(
      final String indexName,
      final String definitionXml,
      final String definitionIdField,
      final int maxPageSize,
      final String engineAlias);

  public abstract DatabaseType getDatabaseVendor();

  public long countWithoutPrefixWithExistsCheck(final String unprefixedIndex) throws IOException {
    if (exists(unprefixedIndex)) {
      return countWithoutPrefix(unprefixedIndex);
    }
    LOG.debug("Index {} does not exist, returning a document count of 0.", unprefixedIndex);
    return 0;
  }

  public String[] convertToPrefixedAliasNames(final String[] indices) {
    return Arrays.stream(indices).map(this::convertToPrefixedAliasName).toArray(String[]::new);
  }

  public static String[] convertToPrefixedAliasNames(
      final String[] indices, final DatabaseClient client) {
    return Arrays.stream(indices)
        .map(i -> convertToPrefixedAliasName(i, client))
        .toArray(String[]::new);
  }

  public String convertToPrefixedAliasName(final String index) {
    final boolean hasExcludePrefix = '-' == index.charAt(0);
    final String rawIndexName = hasExcludePrefix ? index.substring(1) : index;
    final String prefixedIndexName = indexNameService.getOptimizeIndexAliasForIndex(rawIndexName);
    return hasExcludePrefix ? "-" + prefixedIndexName : prefixedIndexName;
  }

  public static String convertToPrefixedAliasName(final String index, final DatabaseClient client) {
    final boolean hasExcludePrefix = '-' == index.charAt(0);
    final String rawIndexName = hasExcludePrefix ? index.substring(1) : index;
    final String prefixedIndexName =
        client.indexNameService.getOptimizeIndexAliasForIndex(rawIndexName);
    return hasExcludePrefix ? "-" + prefixedIndexName : prefixedIndexName;
  }

  protected void validateOperationParams(final ImportRequestDto importRequestDto) {
    if (Objects.isNull(importRequestDto.getType())) {
      throw new OptimizeRuntimeException(
          String.format(
              "The %s param of ImportRequestDto is not set for request", Fields.type.name()));
    }
    if (StringUtils.isBlank(importRequestDto.getIndexName())) {
      throw new OptimizeRuntimeException(
          generateErrorMessageForValidationImportRequestDto(
              importRequestDto.getType(), Fields.indexName.name()));
    }
    if (StringUtils.isBlank(importRequestDto.getId())) {
      throw new OptimizeRuntimeException(
          generateErrorMessageForValidationImportRequestDto(
              importRequestDto.getType(), Fields.id.name()));
    }
    switch (importRequestDto.getType()) {
      case INDEX -> {
        if (Objects.isNull(importRequestDto.getSource())) {
          throw new OptimizeRuntimeException(
              generateErrorMessageForValidationImportRequestDto(
                  RequestType.INDEX, Fields.source.name()));
        }
      }
      case UPDATE -> {
        if (Objects.isNull(importRequestDto.getScriptData())) {
          throw new OptimizeRuntimeException(
              generateErrorMessageForValidationImportRequestDto(
                  RequestType.UPDATE, Fields.scriptData.name()));
        }
      }
      default -> throw new IllegalStateException("Unexpected value: " + importRequestDto.getType());
    }
  }

  protected boolean validateImportName(final ImportRequestDto importRequestDto) {
    if (StringUtils.isBlank(importRequestDto.getImportName())) {
      throw new OptimizeRuntimeException(
          generateErrorMessageForValidationImportRequestDto(
              importRequestDto.getType(), Fields.importName.name()));
    }
    return true;
  }

  protected RetryPolicy<Object> createSnapshotRetryPolicy(final String operation, final int delay) {
    return new RetryPolicy<>()
        .handleIf(
            failure -> {
              if (failure instanceof final RuntimeException statusException) {
                return statusException.getMessage().contains("snapshot_in_progress_exception");
              } else {
                return false;
              }
            })
        .withDelay(Duration.ofSeconds(delay))
        // no retry limit
        .withMaxRetries(-1)
        .onFailedAttempt(
            e -> {
              LOG.warn(
                  "Execution of {} failed due to a pending snapshot operation, details: {}",
                  operation,
                  e.getLastFailure().getMessage());
              LOG.info("Will retry the operation in {} seconds...", delay);
            });
  }

  protected FailsafeExecutor<Object> dbClientSnapshotFailsafe(final String operation) {
    return Failsafe.with(createSnapshotRetryPolicy(operation, snapshotInProgressRetryDelaySeconds));
  }

  public abstract void deleteIndexByRawIndexNames(String... indexNames);

  public abstract void deleteAllIndexes();

  private String generateErrorMessageForValidationImportRequestDto(
      final RequestType type, final String fieldName) {
    return String.format(
        "The %s param of ImportRequestDto is not valid for request type %s", fieldName, type);
  }

  public OptimizeIndexNameService getIndexNameService() {
    return indexNameService;
  }

  public void setSnapshotInProgressRetryDelaySeconds(
      final int snapshotInProgressRetryDelaySeconds) {
    this.snapshotInProgressRetryDelaySeconds = snapshotInProgressRetryDelaySeconds;
  }
}
