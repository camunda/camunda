/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.ORDINAL_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.OrdinalIndex.ORDINAL;
import static io.camunda.optimize.service.db.schema.index.OrdinalIndex.TIMESTAMP_MS;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.OrdinalDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.repository.OrdinalRepository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class OrdinalRepositoryES implements OrdinalRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OrdinalRepositoryES.class);

  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  public OrdinalRepositoryES(
      final OptimizeElasticsearchClient esClient, final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<Integer, Long> loadAllOrdinals() {
    final Map<Integer, Long> result = new HashMap<>();
    try {
      final SearchRequest searchRequest =
          OptimizeSearchRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, ORDINAL_INDEX_NAME)
                      .query(q -> q.matchAll(m -> m))
                      .size(LIST_FETCH_LIMIT)
                      .source(s -> s.filter(f -> f.includes(ORDINAL, TIMESTAMP_MS))));
      final SearchResponse<OrdinalDto> response = esClient.search(searchRequest, OrdinalDto.class);
      for (final Hit<OrdinalDto> hit : response.hits().hits()) {
        final OrdinalDto doc = hit.source();
        if (doc != null) {
          result.put(doc.getOrdinal(), doc.getTimestampMs());
        }
      }
    } catch (final IOException e) {
      LOG.warn("Could not load ordinals from Elasticsearch on startup: {}", e.getMessage());
    } catch (final Exception e) {
      // Index may not exist yet on the very first startup
      LOG.debug("Ordinal index not available on startup (may not exist yet): {}", e.getMessage());
    }
    return result;
  }
}
