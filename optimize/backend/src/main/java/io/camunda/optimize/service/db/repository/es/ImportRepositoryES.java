/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexOperationBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.ImportRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DatabaseHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ImportRepositoryES implements ImportRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(ImportRepositoryES.class);
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;

  public ImportRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ObjectMapper objectMapper,
      final ConfigurationService configurationService,
      final DateTimeFormatter dateTimeFormatter) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
    this.configurationService = configurationService;
    this.dateTimeFormatter = dateTimeFormatter;
  }

  @Override
  public List<TimestampBasedImportIndexDto> getAllTimestampBasedImportIndicesForTypes(
      final List<String> indexTypes) {
    LOG.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest searchRequest =
        OptimizeSearchRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, TIMESTAMP_BASED_IMPORT_INDEX_NAME)
                    .query(
                        q ->
                            q.terms(
                                t ->
                                    t.field(DB_TYPE_INDEX_REFERS_TO)
                                        .terms(
                                            ft ->
                                                ft.value(
                                                    indexTypes.stream()
                                                        .map(FieldValue::of)
                                                        .toList()))))
                    .size(LIST_FETCH_LIMIT));

    final SearchResponse<TimestampBasedImportIndexDto> searchResponse;
    try {
      searchResponse = esClient.search(searchRequest, TimestampBasedImportIndexDto.class);
    } catch (final IOException e) {
      LOG.error("Was not able to get timestamp based import indices!", e);
      throw new OptimizeRuntimeException("Was not able to get timestamp based import indices!", e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.hits(), TimestampBasedImportIndexDto.class, objectMapper);
  }

  @Override
  public <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
      final String indexName,
      final String indexType,
      final Class<T> importDTOClass,
      final String typeIndexComesFrom,
      final D dataSourceDto) {
    LOG.debug("Fetching {} import index of type '{}'", indexType, typeIndexComesFrom);

    GetResponse<T> getResponse = null;
    final GetRequest getRequest =
        OptimizeGetRequestBuilderES.of(
            b ->
                b.optimizeIndex(esClient, indexName)
                    .id(DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto)));
    try {
      getResponse = esClient.get(getRequest, importDTOClass);
    } catch (final IOException e) {
      LOG.error("Could not fetch {} import index", indexType, e);
    }

    if (getResponse != null && getResponse.source() != null) {
      return Optional.of(getResponse.source());
    } else {
      LOG.debug(
          "Was not able to retrieve {} import index for type [{}] and engine [{}] from elasticsearch.",
          indexType,
          typeIndexComesFrom,
          dataSourceDto);
      return Optional.empty();
    }
  }

  @Override
  public void importPositionBasedIndices(
      final String importItemName, final List<PositionBasedImportIndexDto> importIndexDtos) {
    esClient.doImportBulkRequestWithList(
        importItemName,
        importIndexDtos,
        this::addPositionBasedImportIndexRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  @Override
  public void importIndices(
      final String importItemName,
      final List<TimestampBasedImportIndexDto> timestampBasedImportIndexDtos) {
    esClient.doImportBulkRequestWithList(
        importItemName,
        timestampBasedImportIndexDtos,
        this::addImportIndexRequest,
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addPositionBasedImportIndexRequest(
      final BulkRequest.Builder bulkRequestBuilder, final PositionBasedImportIndexDto optimizeDto) {
    LOG.debug(
        "Writing position based import index of type [{}] with position [{}] to elasticsearch",
        optimizeDto.getDbTypeIndexRefersTo(),
        optimizeDto.getPositionOfLastEntity());
    bulkRequestBuilder.operations(
        b ->
            b.index(
                OptimizeIndexOperationBuilderES.of(
                    ib ->
                        ib.optimizeIndex(esClient, POSITION_BASED_IMPORT_INDEX_NAME)
                            .id(
                                DatabaseHelper.constructKey(
                                    optimizeDto.getDbTypeIndexRefersTo(),
                                    optimizeDto.getDataSource()))
                            .document(optimizeDto))));
  }

  private void addImportIndexRequest(
      final BulkRequest.Builder bulkRequestBuilder, final OptimizeDto optimizeDto) {
    bulkRequestBuilder.operations(
        b -> {
          if (optimizeDto instanceof final TimestampBasedImportIndexDto timestampBasedIndexDto) {
            return b.<TimestampBasedImportIndexDto>index(
                OptimizeIndexOperationBuilderES.of(
                    ib -> createTimestampBasedRequest(ib, timestampBasedIndexDto)));
          }
          return b;
        });
  }

  private IndexOperation.Builder<TimestampBasedImportIndexDto> createTimestampBasedRequest(
      final OptimizeIndexOperationBuilderES<TimestampBasedImportIndexDto> builder,
      final TimestampBasedImportIndexDto importIndex) {
    final String currentTimeStamp =
        dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    LOG.debug(
        "Writing timestamp based import index [{}] of type [{}] with execution timestamp [{}] to elasticsearch",
        currentTimeStamp,
        importIndex.getDbTypeIndexRefersTo(),
        importIndex.getLastImportExecutionTimestamp());
    return builder
        .optimizeIndex(esClient, TIMESTAMP_BASED_IMPORT_INDEX_NAME)
        .id(getId(importIndex))
        .document(importIndex);
  }

  private String getId(final TimestampBasedImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(
        importIndex.getDbTypeIndexRefersTo(), importIndex.getDataSourceName());
  }
}
