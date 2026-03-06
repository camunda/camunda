/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.ORDINAL_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.OrdinalIndex.ORDINAL;
import static io.camunda.optimize.service.db.schema.index.OrdinalIndex.TIMESTAMP_MS;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.OrdinalDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.OrdinalRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.HashMap;
import java.util.Map;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OrdinalRepositoryOS implements OrdinalRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OrdinalRepositoryOS.class);

  private final OptimizeOpenSearchClient osClient;
  private final ObjectMapper objectMapper;

  public OrdinalRepositoryOS(
      final OptimizeOpenSearchClient osClient, final ObjectMapper objectMapper) {
    this.osClient = osClient;
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<Integer, Long> loadAllOrdinals() {
    final Map<Integer, Long> result = new HashMap<>();
    try {
      final SearchRequest searchRequest =
          SearchRequest.of(
              b ->
                  b.index(
                          osClient
                              .getIndexNameService()
                              .getOptimizeIndexAliasForIndex(ORDINAL_INDEX_NAME))
                      .query(q -> q.matchAll(m -> m))
                      .size(LIST_FETCH_LIMIT)
                      .source(s -> s.filter(f -> f.includes(ORDINAL, TIMESTAMP_MS))));
      final SearchResponse<OrdinalDto> response = osClient.search(searchRequest, OrdinalDto.class);
      for (final Hit<OrdinalDto> hit : response.hits().hits()) {
        final OrdinalDto doc = hit.source();
        if (doc != null) {
          result.put(doc.getOrdinal(), doc.getTimestampMs());
        }
      }
    } catch (final Exception e) {
      LOG.debug("Ordinal index not available on startup (may not exist yet): {}", e.getMessage());
    }
    return result;
  }
}
