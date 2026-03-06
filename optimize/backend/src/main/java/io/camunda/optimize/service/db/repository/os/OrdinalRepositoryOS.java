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

import io.camunda.optimize.dto.optimize.OrdinalDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.OrdinalRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class OrdinalRepositoryOS implements OrdinalRepository {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(OrdinalRepositoryOS.class);

  private final OptimizeOpenSearchClient osClient;

  public OrdinalRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Map<Integer, Long> loadAllOrdinals() {
    final Map<Integer, Long> result = new HashMap<>();
    try {
      final SearchRequest.Builder requestBuilder =
          new SearchRequest.Builder()
              .index(
                  osClient.getIndexNameService().getOptimizeIndexAliasForIndex(ORDINAL_INDEX_NAME))
              .query(q -> q.matchAll(m -> m))
              .size(LIST_FETCH_LIMIT);
      final List<OrdinalDto> docs = osClient.searchValues(requestBuilder, OrdinalDto.class);
      for (final OrdinalDto doc : docs) {
        result.put(doc.getOrdinal(), doc.getTimestampMs());
      }
    } catch (final Exception e) {
      LOG.debug("Ordinal index not available on startup (may not exist yet): {}", e.getMessage());
    }
    return result;
  }
}
