/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.port.outbound.MappingRuleWritePort;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link MappingRuleWritePort}. */
public class ElasticsearchMappingRuleWriteAdapter implements MappingRuleWritePort {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchMappingRuleWriteAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-mapping-rule";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchMappingRuleWriteAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchMappingRuleWriteAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public void save(final AuthMappingRule mappingRule) {
    LOG.debug(
        "Indexing mapping rule with mappingRuleId={} into index={}",
        mappingRule.mappingRuleId(),
        indexName);
    try {
      client.index(
          request ->
              request.index(indexName).id(mappingRule.mappingRuleId()).document(mappingRule));
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to index mapping rule with mappingRuleId=" + mappingRule.mappingRuleId(), e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error indexing mapping rule with mappingRuleId=" + mappingRule.mappingRuleId(), e);
    }
  }

  @Override
  public void deleteById(final String mappingRuleId) {
    LOG.debug("Deleting mapping rule by id={} from index={}", mappingRuleId, indexName);
    try {
      client.delete(request -> request.index(indexName).id(mappingRuleId));
    } catch (final ElasticsearchException e) {
      LOG.warn(
          "Failed to delete mapping rule with id={}: {}", mappingRuleId, e.getMessage());
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error deleting mapping rule with id=" + mappingRuleId + " from index=" + indexName,
          e);
    }
  }
}
