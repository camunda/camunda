/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_RULE_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;

import io.camunda.optimize.dto.optimize.access.MappingRuleDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.MappingRuleRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class MappingRuleRepositoryOS implements MappingRuleRepository {

  private final OptimizeOpenSearchClient osClient;

  public MappingRuleRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Optional<MappingRuleDto> get(final String mappingRuleId) {
    final GetResponse<Map> response =
        osClient.get(
            new GetRequest.Builder().index(MAPPING_RULE_INDEX_NAME).id(mappingRuleId),
            Map.class,
            "Could not read mapping rule " + mappingRuleId);
    return response.found()
        ? Optional.ofNullable(response.source()).map(MappingRuleDto::fromDocument)
        : Optional.empty();
  }

  @Override
  public List<MappingRuleDto> getAll() {
    return osClient
        .scrollValues(
            new SearchRequest.Builder()
                .index(MAPPING_RULE_INDEX_NAME)
                .query(matchAll())
                .size(LIST_FETCH_LIMIT),
            Map.class)
        .stream()
        .map(document -> MappingRuleDto.fromDocument((Map<String, Object>) document))
        .toList();
  }

  @Override
  public void upsert(final MappingRuleDto mappingRule) {
    osClient.index(
        new IndexRequest.Builder<Map<String, Object>>()
            .index(MAPPING_RULE_INDEX_NAME)
            .id(mappingRule.mappingRuleId())
            .refresh(Refresh.True)
            .document(mappingRule.toDocument()));
  }

  @Override
  public void delete(final String mappingRuleId) {
    osClient.delete(
        new DeleteRequest.Builder()
            .index(MAPPING_RULE_INDEX_NAME)
            .id(mappingRuleId)
            .refresh(Refresh.True),
        "Could not delete mapping rule " + mappingRuleId);
  }
}
