/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.process;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.util.modelreader.ProcessModelReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticSearchProcessCacheLoader implements CacheLoader<Long, CachedProcessEntity> {

  private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchProcessCacheLoader.class);

  private final ElasticsearchClient client;
  private final String processIndexName;

  public ElasticSearchProcessCacheLoader(
      final ElasticsearchClient client, final String processIndexName) {
    this.client = client;
    this.processIndexName = processIndexName;
  }

  @Override
  public CachedProcessEntity load(final Long processDefinitionKey) throws IOException {
    final var response =
        client.get(
            request -> request.index(processIndexName).id(String.valueOf(processDefinitionKey)),
            ProcessEntity.class);
    if (response.found()) {
      final var processEntity = response.source();
      return new CachedProcessEntity(
          processEntity.getName(),
          processEntity.getVersionTag(),
          extractCallActivityIdsFromDiagram(processEntity));
    } else {
      // This should only happen if the process was deleted from ElasticSearch which should never
      // happen. Normally, the process is exported before the process instance is exporter. So the
      // process should be found in ElasticSearch index.
      LOG.debug("Process '{}' not found in Elasticsearch", processDefinitionKey);
      return null;
    }
  }

  private List<String> extractCallActivityIdsFromDiagram(final ProcessEntity processEntity) {
    final String bpmnXml = processEntity.getBpmnXml();
    return ProcessModelReader.of(
            bpmnXml.getBytes(StandardCharsets.UTF_8), processEntity.getBpmnProcessId())
        .map(
            reader ->
                reader.extractCallActivities().stream().map(BaseElement::getId).sorted().toList())
        .orElseGet(ArrayList::new);
  }
}
