/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.ImportIndexDto;
import org.camunda.optimize.dto.optimize.index.PositionBasedImportIndexDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.db.repository.ImportRepository;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.DatabaseHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.POSITION_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Slf4j
@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class ImportRepositoryES implements ImportRepository {
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;
  private final ConfigurationService configurationService;

  @Override
  public List<TimestampBasedImportIndexDto> getAllTimestampBasedImportIndicesForTypes(List<String> indexTypes) {
    log.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest searchRequest = new SearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(new SearchSourceBuilder()
                .query(termsQuery(DB_TYPE_INDEX_REFERS_TO, indexTypes))
                .size(LIST_FETCH_LIMIT));

    final SearchResponse searchResponse;
    try {
      searchResponse = esClient.search(searchRequest);
    } catch (IOException e) {
      log.error("Was not able to get timestamp based import indices!", e);
      throw new OptimizeRuntimeException("Was not able to get timestamp based import indices!", e);
    }
    return ElasticsearchReaderUtil.mapHits(searchResponse.getHits(), TimestampBasedImportIndexDto.class, objectMapper);
  }

  @Override
  public <T extends ImportIndexDto<D>, D extends DataSourceDto> Optional<T> getImportIndex(
    String indexName,
    String indexType,
    Class<T> importDTOClass,
    String typeIndexComesFrom,
    D dataSourceDto
  ) {
    log.debug("Fetching {} import index of type '{}'", indexType, typeIndexComesFrom);

    GetResponse getResponse = null;
    GetRequest getRequest = new GetRequest(indexName)
      .id(DatabaseHelper.constructKey(typeIndexComesFrom, dataSourceDto));
    try {
      getResponse = esClient.get(getRequest);
    } catch (IOException e) {
      log.error("Could not fetch {} import index", indexType, e);
    }

    if (getResponse != null && getResponse.isExists()) {
      String content = getResponse.getSourceAsString();
      try {
        return Optional.of(objectMapper.readValue(content, importDTOClass));
      } catch (IOException e) {
        log.debug("Error while reading {} import index from elasticsearch!", indexType, e);
        return Optional.empty();
      }
    } else {
      log.debug(
        "Was not able to retrieve {} import index for type [{}] and engine [{}] from elasticsearch.",
        indexType,
        typeIndexComesFrom,
        dataSourceDto
      );
      return Optional.empty();
    }
  }

  @Override
  public void importPositionBasedIndices(String importItemName, List<PositionBasedImportIndexDto> importIndexDtos) {
    esClient.doImportBulkRequestWithList(
      importItemName,
      importIndexDtos,
      this::addPositionBasedImportIndexRequest,
      configurationService.getSkipDataAfterNestedDocLimitReached()
    );
  }

  private void addPositionBasedImportIndexRequest(BulkRequest bulkRequest, PositionBasedImportIndexDto optimizeDto) {
    log.debug(
      "Writing position based import index of type [{}] with position [{}] to elasticsearch",
      optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getPositionOfLastEntity()
    );
    try {
      bulkRequest.add(new IndexRequest(POSITION_BASED_IMPORT_INDEX_NAME)
                        .id(DatabaseHelper.constructKey(optimizeDto.getEsTypeIndexRefersTo(), optimizeDto.getDataSource()))
                        .source(objectMapper.writeValueAsString(optimizeDto), XContentType.JSON));
    } catch (JsonProcessingException e) {
      log.error("Was not able to write position based import index of type [{}] to Elasticsearch. Reason: {}",
                optimizeDto.getEsTypeIndexRefersTo(), e
      );
    }
  }
}
