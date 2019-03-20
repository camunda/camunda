/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.reader;

import java.util.List;
import java.util.function.Consumer;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.util.ElasticsearchUtil;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AbstractReader {

  @Autowired
  protected TransportClient esClient;

  @Autowired
  protected ObjectMapper objectMapper;

  protected <T extends OperateEntity> List<T> scroll(SearchRequestBuilder builder, Class<T> clazz) {
    return ElasticsearchUtil.scroll(builder, clazz, objectMapper, esClient);
  }

  protected <T extends OperateEntity> List<T> scroll(SearchRequestBuilder builder, Class<T> clazz,
    Consumer<Aggregations> aggsProcessor) {
    return ElasticsearchUtil.scroll(builder, clazz, objectMapper, esClient, null, aggsProcessor);
  }

}
