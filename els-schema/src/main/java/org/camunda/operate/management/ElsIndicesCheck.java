/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.management;

import static org.camunda.operate.util.CollectionUtil.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.operate.es.RetryElasticsearchClient;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElsIndicesCheck {

  private static Logger logger = LoggerFactory.getLogger(ElsIndicesCheck.class);

  @Autowired
  private RetryElasticsearchClient retryElasticsearchClient;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private List<IndexDescriptor> indexDescriptors;

  @Autowired
  private List<TemplateDescriptor> templateDescriptors;

  public boolean indicesArePresent() {
    try {
      final ClusterHealthResponse clusterHealthResponse = retryElasticsearchClient.clusterHealth(
          new ClusterHealthRequest(operateProperties.getElasticsearch().getIndexPrefix() + "*"));
      Map<String, ClusterIndexHealth> indicesStatus = clusterHealthResponse.getIndices();
      List<String> allIndexNames = new ArrayList<>();
      allIndexNames.addAll(map(indexDescriptors, IndexDescriptor::getIndexName));
      allIndexNames.addAll(map(templateDescriptors, TemplateDescriptor::getMainIndexName));
      return indicesStatus.keySet().containsAll(allIndexNames);
    } catch (Exception e) {
      logger.error("ClusterHealth request failed", e);
      return false;
    }
  }

}
