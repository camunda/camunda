/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.ImportRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DatabaseHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ImportRepositoryOS implements ImportRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ImportRepositoryOS.class);
  private final OptimizeOpenSearchClient osClient;
  private final ConfigurationService configurationService;
  private final OptimizeIndexNameService indexNameService;
  private final DateTimeFormatter dateTimeFormatter;

  public ImportRepositoryOS(
      final OptimizeOpenSearchClient osClient,
      final ConfigurationService configurationService,
      final OptimizeIndexNameService indexNameService,
      final DateTimeFormatter dateTimeFormatter) {
    this.osClient = osClient;
    this.configurationService = configurationService;
    this.indexNameService = indexNameService;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public List<TimestampBasedImportIndexDto> getAllTimestampBasedImportIndicesForTypes(
      final List<String> indexTypes) {
    LOG.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest.Builder requestBuilder =
        new SearchRequest.Builder()
            .index(
                indexNameService.getOptimizeIndexAliasForIndex(TIMESTAMP_BASED_IMPORT_INDEX_NAME))
            .query(stringTerms(DB_TYPE_INDEX_REFERS_TO, indexTypes))
            .size(LIST_FETCH_LIMIT);

    return osClient.searchValues(requestBuilder, TimestampBasedImportIndexDto.class);
  }

  @Override
  public <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
      final String indexName,
      final String indexType,
      final Class<T> importDTOClass,
      final String typeIndexComesFrom,
      final D dataSourceDto) {
    LOG.debug("Fetching {} import index of type '{}'", indexType, typeIndexComesFrom);
    final GetResponse<T> response =
        osClient.get(
            indexNameService.getOptimizeIndexAliasForIndex(indexName),
            DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto),
            importDTOClass,
            format("Could not fetch %s import index", indexType));

    if (response.found()) {
      return Optional.ofNullable(response.source());
    } else {
      LOG.debug(
          "Was not able to retrieve {} import index for type [{}] and engine [{}] from opensearch.",
          indexType,
          typeIndexComesFrom,
          dataSourceDto);
      return Optional.empty();
    }
  }

  @Override
  public void importPositionBasedIndices(
      final String importItemName, final List<PositionBasedImportIndexDto> importIndexDtos) {
    osClient.doImportBulkRequestWithList(
        importItemName,
        importIndexDtos,
        this::addPositionBasedImportIndexRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void importIndices(
      final String importItemName,
      final List<TimestampBasedImportIndexDto> timestampBasedImportIndexDtos) {
    osClient.doImportBulkRequestWithList(
        importItemName,
        timestampBasedImportIndexDtos,
        this::addImportIndexRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private BulkOperation addPositionBasedImportIndexRequest(
      final PositionBasedImportIndexDto optimizeDto) {
    LOG.debug(
        "Writing position based import index of type [{}] with position [{}] to opensearch",
        optimizeDto.getEsTypeIndexRefersTo(),
        optimizeDto.getPositionOfLastEntity());
    // leaving the prefix "es" although it is valid for ES and OS,
    // since changing this would require data migration and the cost/benefit of the change is not
    // worth the effort
    return new BulkOperation.Builder()
        .index(
            new IndexOperation.Builder<PositionBasedImportIndexDto>()
                .index(
                    indexNameService.getOptimizeIndexAliasForIndex(
                        POSITION_BASED_IMPORT_INDEX_NAME))
                .id(
                    DatabaseHelper.constructKey(
                        optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getDataSource()))
                .document(optimizeDto)
                .build())
        .build();
  }

  private BulkOperation addImportIndexRequest(final OptimizeDto optimizeDto) {
    if (optimizeDto instanceof final TimestampBasedImportIndexDto timestampBasedIndexDto) {
      return createTimestampBasedRequest(timestampBasedIndexDto);
    } else {
      throw new OptimizeRuntimeException(
          format(
              "Import bulk operation is not supported for %s", optimizeDto.getClass().getName()));
    }
  }

  private BulkOperation createTimestampBasedRequest(
      final TimestampBasedImportIndexDto importIndex) {
    final String currentTimeStamp =
        dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    LOG.debug(
        "Writing timestamp based import index [{}] of type [{}] with execution timestamp [{}] to opensearch",
        currentTimeStamp,
        importIndex.getEsTypeIndexRefersTo(),
        importIndex.getLastImportExecutionTimestamp());
    return new BulkOperation.Builder()
        .index(
            new IndexOperation.Builder<TimestampBasedImportIndexDto>()
                .index(
                    indexNameService.getOptimizeIndexAliasForIndex(
                        TIMESTAMP_BASED_IMPORT_INDEX_NAME))
                .id(getId(importIndex))
                .document(importIndex)
                .build())
        .build();
  }

  private String getId(final TimestampBasedImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(
        importIndex.getEsTypeIndexRefersTo(), importIndex.getDataSourceName());
  }
}
