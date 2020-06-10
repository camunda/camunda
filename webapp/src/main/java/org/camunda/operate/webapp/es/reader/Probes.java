/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.es.reader;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.camunda.operate.schema.indices.IndexDescriptor;
import org.camunda.operate.schema.templates.TemplateDescriptor;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterIndexHealth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.camunda.operate.util.CollectionUtil.map;

// Note: Possible checks for
// TODO: Importer is running
// TODO: Archiver is running
@Component
public class Probes {

  public static final Long FIFTY_SECONDS = 50 * 1000L;
  
  private static Logger logger = LoggerFactory.getLogger(Probes.class);
  
  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  List<IndexDescriptor> indexDescriptors;
  
  @Autowired
  List<TemplateDescriptor> templateDescriptors;
  
  public boolean isReady() {
    return indicesArePresent();
  }
  
  public boolean isLive() {
    return isLive(FIFTY_SECONDS);
  }
  
  public boolean isLive(long maxDurationInMs) {
    Instant start = Instant.now();
    boolean isReady = indicesArePresent();
    Instant stop = Instant.now();
    return isReady && Duration.between(start, stop).toMillis() < maxDurationInMs;
  }

  private boolean indicesArePresent() {
    try {
      final ClusterHealthResponse clusterHealthResponse = esClient.cluster().health(new ClusterHealthRequest(operateProperties.getElasticsearch().getIndexPrefix()+"*"), RequestOptions.DEFAULT);
      Map<String,ClusterIndexHealth> indicesStatus = clusterHealthResponse.getIndices();
      List<String> allIndexNames = new ArrayList<String>();
      allIndexNames.addAll(map(indexDescriptors,i -> i.getIndexName()));
      allIndexNames.addAll(map(templateDescriptors,t -> t.getMainIndexName()));
      return indicesStatus.keySet().containsAll(allIndexNames);
    } catch (Throwable e) {
      logger.error("ClusterHealth request failed",e);
      return false;
    }
  }

}
