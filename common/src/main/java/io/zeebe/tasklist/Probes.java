/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist;

import static io.zeebe.tasklist.util.CollectionUtil.map;

import io.zeebe.tasklist.es.schema.indices.IndexDescriptor;
import io.zeebe.tasklist.es.schema.templates.TemplateDescriptor;
import io.zeebe.tasklist.property.TasklistProperties;
import java.time.Duration;
import java.time.Instant;
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

// Note: Possible checks for
// TODO: Importer is running
// TODO: Archiver is running
@Component
public class Probes {

  public static final Long FIFTY_SECONDS = 50 * 1000L;

  private static final Logger LOGGER = LoggerFactory.getLogger(Probes.class);
  @Autowired private List<IndexDescriptor> indexDescriptors;
  @Autowired private List<TemplateDescriptor> templateDescriptors;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private TasklistProperties tasklistProperties;

  public boolean isReady() {
    return indicesArePresent();
  }

  public boolean isLive() {
    return isLive(FIFTY_SECONDS);
  }

  public boolean isLive(long maxDurationInMs) {
    final Instant start = Instant.now();
    final boolean isReady = indicesArePresent();
    final Instant stop = Instant.now();
    return isReady && Duration.between(start, stop).toMillis() < maxDurationInMs;
  }

  private boolean indicesArePresent() {
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
      allIndexNames.addAll(map(indexDescriptors, i -> i.getIndexName()));
      allIndexNames.addAll(map(templateDescriptors, t -> t.getMainIndexName()));
      return indicesStatus.keySet().containsAll(allIndexNames);
    } catch (Exception e) {
      LOGGER.error("ClusterHealth request failed", e);
      return false;
    }
  }
}
