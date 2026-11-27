/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.MAPPING_RULE_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.access.MappingRuleDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.MappingRuleRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class MappingRuleRepositoryES implements MappingRuleRepository {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public MappingRuleRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<MappingRuleDto> get(final String mappingRuleId) {
    try {
      final GetResponse<Map> response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, MAPPING_RULE_INDEX_NAME).id(mappingRuleId)),
              Map.class);
      return response.found()
          ? Optional.ofNullable(response.source()).map(MappingRuleDto::fromDocument)
          : Optional.empty();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read mapping rule " + mappingRuleId, e);
    }
  }

  @Override
  public List<MappingRuleDto> getAll() {
    final int scrollTimeout =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final SearchResponse<Map> response;
    try {
      response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, MAPPING_RULE_INDEX_NAME)
                          .query(q -> q.matchAll(m -> m))
                          .size(LIST_FETCH_LIMIT)
                          .scroll(Time.of(t -> t.time(scrollTimeout + "s")))),
              Map.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read mapping rules", e);
    }
    return ElasticsearchReaderUtil.retrieveAllScrollResults(
            response, Map.class, objectMapper, esClient, scrollTimeout)
        .stream()
        .map(document -> MappingRuleDto.fromDocument((Map<String, Object>) document))
        .toList();
  }

  @Override
  public void upsert(final MappingRuleDto mappingRule) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, MAPPING_RULE_INDEX_NAME)
                      .id(mappingRule.mappingRuleId())
                      .refresh(Refresh.True)
                      .document(mappingRule.toDocument())));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Could not persist mapping rule " + mappingRule.mappingRuleId(), e);
    }
  }

  @Override
  public void delete(final String mappingRuleId) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, MAPPING_RULE_INDEX_NAME)
                      .id(mappingRuleId)
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete mapping rule " + mappingRuleId, e);
    }
  }
}
