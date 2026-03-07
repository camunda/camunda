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
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.auth.domain.model.AuthMappingRule;
import io.camunda.auth.domain.port.outbound.MappingRuleReadPort;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Elasticsearch-backed implementation of {@link MappingRuleReadPort}. */
public class ElasticsearchMappingRuleReadAdapter implements MappingRuleReadPort {

  private static final Logger LOG =
      LoggerFactory.getLogger(ElasticsearchMappingRuleReadAdapter.class);

  private static final String DEFAULT_INDEX_NAME = "camunda-auth-mapping-rule";

  private final ElasticsearchClient client;
  private final String indexName;

  public ElasticsearchMappingRuleReadAdapter(final ElasticsearchClient client) {
    this(client, DEFAULT_INDEX_NAME);
  }

  public ElasticsearchMappingRuleReadAdapter(
      final ElasticsearchClient client, final String indexName) {
    this.client = client;
    this.indexName = indexName;
  }

  @Override
  public Optional<AuthMappingRule> findById(final String mappingRuleId) {
    LOG.debug("Fetching mapping rule by id={} from index={}", mappingRuleId, indexName);
    try {
      final GetResponse<AuthMappingRule> response =
          client.get(request -> request.index(indexName).id(mappingRuleId), AuthMappingRule.class);
      if (response.found() && response.source() != null) {
        return Optional.of(response.source());
      }
      return Optional.empty();
    } catch (final ElasticsearchException e) {
      throw new RuntimeException(
          "Failed to get mapping rule with id=" + mappingRuleId + " from index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException(
          "I/O error fetching mapping rule with id=" + mappingRuleId + " from index=" + indexName,
          e);
    }
  }

  @Override
  public List<AuthMappingRule> findAll() {
    LOG.debug("Fetching all mapping rules from index={}", indexName);
    try {
      final SearchResponse<AuthMappingRule> response =
          client.search(
              request -> request.index(indexName).query(q -> q.matchAll(m -> m)).size(10_000),
              AuthMappingRule.class);
      return response.hits().hits().stream()
          .map(Hit::source)
          .filter(doc -> doc != null)
          .collect(Collectors.toList());
    } catch (final ElasticsearchException e) {
      throw new RuntimeException("Failed to search mapping rules in index=" + indexName, e);
    } catch (final IOException e) {
      throw new RuntimeException("I/O error searching mapping rules in index=" + indexName, e);
    }
  }
}
