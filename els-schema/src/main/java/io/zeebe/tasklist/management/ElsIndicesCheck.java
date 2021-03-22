/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.management;

import static io.zeebe.tasklist.util.CollectionUtil.map;

import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.schema.templates.TemplateDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElsIndicesCheck {

  private static Logger logger = LoggerFactory.getLogger(ElsIndicesCheck.class);

  @Autowired private RestHighLevelClient esClient;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private List<IndexDescriptor> indexDescriptors;

  @Autowired private List<TemplateDescriptor> templateDescriptors;

  public boolean indicesArePresent() {
    try {
      final ClusterHealthResponse clusterHealthResponse =
          esClient
              .cluster()
              .health(
                  new ClusterHealthRequest(
                      tasklistProperties.getElasticsearch().getIndexPrefix() + "*"),
                  RequestOptions.DEFAULT);
      final Map<String, ClusterIndexHealth> indicesStatus = clusterHealthResponse.getIndices();
      final List<String> allIndexNames = new ArrayList<String>();
      allIndexNames.addAll(map(indexDescriptors, i -> i.getFullQualifiedName()));
      allIndexNames.addAll(map(templateDescriptors, t -> t.getFullQualifiedName()));
      return indicesStatus.keySet().containsAll(allIndexNames);
    } catch (Exception e) {
      logger.error("ClusterHealth request failed", e);
      return false;
    }
  }
}
