/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.cache.process;

import com.github.benmanes.caffeine.cache.CacheLoader;
import io.camunda.exporter.utils.XMLUtil;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSearchProcessCacheLoader implements CacheLoader<Long, CachedProcessEntity> {
  private static final Logger LOG = LoggerFactory.getLogger(OpenSearchProcessCacheLoader.class);

  private final OpenSearchClient client;
  private final String processIndexName;
  private final XMLUtil xmlUtil;

  public OpenSearchProcessCacheLoader(
      final OpenSearchClient client, final String processIndexName, final XMLUtil xmlUtil) {
    this.client = client;
    this.processIndexName = processIndexName;
    this.xmlUtil = xmlUtil;
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
      // This should only happen if the process was deleted from OpenSearch which should never
      // happen. Normally, the process is exported before the process instance is exporter. So the
      // process should be found in OpenSearch index.
      LOG.debug("Process '{}' not found in OpenSearch", processDefinitionKey);
      return null;
    }
  }

  private List<String> extractCallActivityIdsFromDiagram(final ProcessEntity processEntity) {
    final String bpmnXml = processEntity.getBpmnXml();
    final Optional<ProcessEntity> diagramData =
        xmlUtil.extractDiagramData(bpmnXml.getBytes(), processEntity.getBpmnProcessId());
    return diagramData.isPresent() ? diagramData.get().getCallActivityIds() : new ArrayList<>();
  }
}
