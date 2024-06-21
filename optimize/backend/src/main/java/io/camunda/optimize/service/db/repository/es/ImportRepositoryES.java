/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.IMPORT_INDEX_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.index.AllEntitiesBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.EngineImportIndexDto;
import io.camunda.optimize.dto.optimize.index.ImportIndexDto;
import io.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import io.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.ImportRepository;
import io.camunda.optimize.service.db.schema.index.index.ImportIndexIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.DatabaseHelper;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentBuilder;
import org.elasticsearch.xcontent.XContentFactory;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ImportRepositoryES implements ImportRepository {

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;
  private final DateTimeFormatter dateTimeFormatter;

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
  public Optional<AllEntitiesBasedImportIndexDto> getImportIndex(final String id) {
    final GetRequest getRequest = new GetRequest(IMPORT_INDEX_INDEX_NAME).id(id);

    GetResponse getResponse = null;
    try {
      getResponse = esClient.get(getRequest);
    } catch (final Exception ignored) {
      // do nothing
    }

    if (getResponse != null && getResponse.isExists()) {
      try {
        final AllEntitiesBasedImportIndexDto storedIndex =
            objectMapper.readValue(
                getResponse.getSourceAsString(), AllEntitiesBasedImportIndexDto.class);
        return Optional.of(storedIndex);
      } catch (final IOException e) {
        log.error("Was not able to retrieve import index of [{}]. Reason: {}", id, e);
        return Optional.empty();
      }
    } else {
      log.debug(
          "Was not able to retrieve import index for type '{}' from Elasticsearch. "
              + "Desired index does not exist.",
          id);
      return Optional.empty();
    }
  }

  @Override
  public void importIndices(
      final String importItemName, final List<EngineImportIndexDto> engineImportIndexDtos) {
    esClient.doImportBulkRequestWithList(
        importItemName,
        engineImportIndexDtos,
        this::addImportIndexRequest,
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

  private void addImportIndexRequest(final BulkRequest bulkRequest, final OptimizeDto optimizeDto) {
    if (optimizeDto instanceof final TimestampBasedImportIndexDto timestampBasedIndexDto) {
      bulkRequest.add(createTimestampBasedRequest(timestampBasedIndexDto));
    } else if (optimizeDto instanceof final AllEntitiesBasedImportIndexDto entitiesBasedIndexDto) {
      bulkRequest.add(createAllEntitiesBasedRequest(entitiesBasedIndexDto));
    }
  }

  private IndexRequest createTimestampBasedRequest(final TimestampBasedImportIndexDto importIndex) {
    final String currentTimeStamp = dateTimeFormatter.format(
        importIndex.getTimestampOfLastEntity());
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

  private String getId(final EngineImportIndexDto importIndex) {
    return DatabaseHelper.constructKey(
        importIndex.getEsTypeIndexRefersTo(), importIndex.getEngine());
  }

  private IndexRequest createAllEntitiesBasedRequest(
      final AllEntitiesBasedImportIndexDto importIndex) {
    log.debug(
        "Writing all entities based import index type [{}] to elasticsearch. "
            + "Starting from [{}]",
        importIndex.getEsTypeIndexRefersTo(),
        importIndex.getImportIndex());
    try {
      final XContentBuilder sourceToAdjust =
          XContentFactory.jsonBuilder()
              .startObject()
              .field(ImportIndexIndex.ENGINE, importIndex.getEngine())
              .field(ImportIndexIndex.IMPORT_INDEX, importIndex.getImportIndex())
              .endObject();
      return new IndexRequest(IMPORT_INDEX_INDEX_NAME)
          .id(getId(importIndex))
          .source(sourceToAdjust);
    } catch (final IOException e) {
      log.error(
          "Was not able to write all entities based import index of type [{}] to Elasticsearch. Reason: {}",
          importIndex.getEsTypeIndexRefersTo(),
          e);
      return new IndexRequest();
    }
  }
}
