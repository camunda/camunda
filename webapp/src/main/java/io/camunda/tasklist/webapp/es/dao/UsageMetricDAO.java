/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.es.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.MetricEntity;
import io.camunda.tasklist.schema.indices.MetricIndex;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UsageMetricDAO extends GenericDAO<MetricEntity, MetricIndex> {
  @Autowired
  public UsageMetricDAO(
      ObjectMapper objectMapper, MetricIndex index, RestHighLevelClient esClient) {
    super(objectMapper, index, esClient);
  }
}
