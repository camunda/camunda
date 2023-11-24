/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.reader.importindex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.index.TimestampBasedImportIndexDto;
import org.camunda.optimize.service.db.reader.importindex.TimestampBasedImportIndexReader;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.db.schema.index.index.TimestampBasedImportIndex.DB_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.service.db.DatabaseConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class TimestampBasedImportIndexReaderES extends AbstractImportIndexReaderES<TimestampBasedImportIndexDto, DataSourceDto>
  implements TimestampBasedImportIndexReader {

  public TimestampBasedImportIndexReaderES(final OptimizeElasticsearchClient esClient,
                                           final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  @Override
  public String getImportIndexType() {
    return "timestamp based";
  }

  @Override
  public String getImportIndexName() {
    return TIMESTAMP_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  public Class<TimestampBasedImportIndexDto> getImportDTOClass() {
    return TimestampBasedImportIndexDto.class;
  }

  @Override
  public List<TimestampBasedImportIndexDto> getAllImportIndicesForTypes(List<String> indexTypes) {
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

}
