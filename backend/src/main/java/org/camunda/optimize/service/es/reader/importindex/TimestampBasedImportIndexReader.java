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
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.reader.ElasticsearchReaderUtil;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static org.camunda.optimize.service.es.schema.index.index.TimestampBasedImportIndex.ES_TYPE_INDEX_REFERS_TO;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.LIST_FETCH_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TIMESTAMP_BASED_IMPORT_INDEX_NAME;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;

@Component
@Slf4j
public class TimestampBasedImportIndexReader
  extends AbstractImportIndexReader<TimestampBasedImportIndexDto, DataSourceDto> {

  public TimestampBasedImportIndexReader(final OptimizeElasticsearchClient esClient,
                                         final ObjectMapper objectMapper) {
    super(esClient, objectMapper);
  }

  @Override
  protected String getImportIndexType() {
    return "timestamp based";
  }

  @Override
  protected String getImportIndexName() {
    return TIMESTAMP_BASED_IMPORT_INDEX_NAME;
  }

  @Override
  protected Class<TimestampBasedImportIndexDto> getImportDTOClass() {
    return TimestampBasedImportIndexDto.class;
  }

  public List<TimestampBasedImportIndexDto> getAllImportIndicesForTypes(List<String> indexTypes) {
    log.debug("Fetching timestamp based import indices of types '{}'", indexTypes);

    final SearchRequest searchRequest = new SearchRequest(TIMESTAMP_BASED_IMPORT_INDEX_NAME)
      .source(new SearchSourceBuilder()
                .query(termsQuery(ES_TYPE_INDEX_REFERS_TO, indexTypes))
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
