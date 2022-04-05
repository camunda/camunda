/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.es.reader;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.entities.OperateEntity;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AbstractReader {

  @Autowired
  protected RestHighLevelClient esClient;

  @Autowired
  protected ObjectMapper objectMapper;

  protected <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz) throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, clazz, objectMapper, esClient);
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz,
    Consumer<Aggregations> aggsProcessor) throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, clazz, objectMapper, esClient, null, aggsProcessor);
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequest searchRequest, Class<T> clazz,
      Consumer<SearchHits> searchHitsProcessor, Consumer<Aggregations> aggsProcessor) throws IOException {
    return ElasticsearchUtil.scroll(searchRequest, clazz, objectMapper, esClient, searchHitsProcessor, aggsProcessor);
  }

}
