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
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
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
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class ImportRepositoryES implements ImportRepository {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(ImportRepositoryES.class);
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
    log.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest searchRequest =
        new SearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
            .source(
                new SearchSourceBuilder()
                    .query(termsQuery(DB_TYPE_INDEX_REFERS_TO, indexTypes))
                    .size(LIST_FETCH_LIMIT));

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (final IOException e) {
      log.error("Was not able to get timestamp based import indices!", e);
      throw new OptimizeRuntimeException("Was not able to get timestamp based import indices!", e);
    }
    return ElasticsearchReaderUtil.mapHits(
        searchResponse.getHits(), TimestampBasedImportIndexDto.class, objectMapper);
  }

  @Override
  public <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
      final String indexName,
      final String indexType,
      final Class<T> importDTOClass,
      final String typeIndexComesFrom,
      final D dataSourceDto) {
    log.debug("Fetching {} import index of type '{}'", indexType, typeIndexComesFrom);

    GetResponse getResponse = null;
    final GetRequest getRequest =
        new GetRequest(indexName)
            .id(DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto));
    try {
      getResponse = esClient.get(getRequest);
    } catch (final IOException e) {
      log.error("Could not fetch {} import index", indexType, e);
    }

    if (getResponse != null && getResponse.isExists()) {
      final String content = getResponse.getSourceAsString();
      try {
        return Optional.of(objectMapper.readValue(content, importDTOClass));
      } catch (final IOException e) {
        log.debug("Error while reading {} import index from elasticsearch!", indexType, e);
        return Optional.empty();
      }
    } else {
      log.debug(
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
        (bulkRequest, optimizeDto) -> bulkRequest.add(createTimestampBasedRequest(optimizeDto)),
        configurationService.getSkipDataAfterNestedDocLimitReached());
  }

  private void addPositionBasedImportIndexRequest(
      final BulkRequest bulkRequest, final PositionBasedImportIndexDto optimizeDto) {
    log.debug(
        "Writing position based import index of type [{}] with position [{}] to elasticsearch",
        optimizeDto.getEsTypeIndexRefersTo(),
        optimizeDto.getPositionOfLastEntity());
    try {
      bulkRequest.add(
          new IndexRequest(POSITION_BASED_IMPORT_INDEX_NAME)
              .id(
                  DatabaseHelper.constructKey(
                      optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getDataSource()))
              .source(objectMapper.writeValueAsString(optimizeDto), XContentType.JSON));
    } catch (final JsonProcessingException e) {
      log.error(
          "Was not able to write position based import index of type [{}] to Elasticsearch. Reason: {}",
          optimizeDto.getEsTypeIndexRefersTo(),
          e);
    }
  }

  private IndexRequest createTimestampBasedRequest(final TimestampBasedImportIndexDto importIndex) {
    final String currentTimeStamp =
        dateTimeFormatter.format(importIndex.getTimestampOfLastEntity());
    log.debug(
        "Writing timestamp based import index [{}] of type [{}] with execution timestamp [{}] to elasticsearch",
        currentTimeStamp,
        importIndex.getEsTypeIndexRefersTo(),
        importIndex.getLastImportExecutionTimestamp());
    try {
      return new IndexRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
          .id(getId(importIndex))
          .source(objectMapper.writeValueAsString(importIndex), XContentType.JSON);
    } catch (final JsonProcessingException e) {
      log.error(
          "Was not able to write timestamp based import index of type [{}] to Elasticsearch. Reason: {}",
          importIndex.getEsTypeIndexRefersTo(),
          e);
      return new IndexRequest();
    }
  }

  private String getId(final TimestampBasedImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(
        importIndex.getEsTypeIndexRefersTo(), importIndex.getDataSourceName());
  }
}
