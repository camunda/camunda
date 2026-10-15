/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.ACCESS_MAPPING_RULE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ACCESS_ROLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;

import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchAllQuery;
import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class AccessRepositoryES implements AccessRepository {

  private final OptimizeElasticsearchClient esClient;

  public AccessRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public List<AccessMappingRuleDto> findAllMappingRules() {
    return findAll(ACCESS_MAPPING_RULE_INDEX_NAME, AccessMappingRuleDto.class);
  }

  @Override
  public List<AccessRoleDto> findAllRoles() {
    return findAll(ACCESS_ROLE_INDEX_NAME, AccessRoleDto.class);
  }

  @Override
  public boolean createMappingRuleIfMissing(final AccessMappingRuleDto rule) {
    return createIfMissing(
        ACCESS_MAPPING_RULE_INDEX_NAME, rule.getId(), rule, AccessMappingRuleDto.class);
  }

  @Override
  public boolean createRoleIfMissing(final AccessRoleDto role) {
    return createIfMissing(ACCESS_ROLE_INDEX_NAME, role.getId(), role, AccessRoleDto.class);
  }

  @Override
  public void saveMappingRule(final AccessMappingRuleDto rule) {
    save(ACCESS_MAPPING_RULE_INDEX_NAME, rule.getId(), rule);
  }

  @Override
  public void saveRole(final AccessRoleDto role) {
    save(ACCESS_ROLE_INDEX_NAME, role.getId(), role);
  }

  @Override
  public void deleteMappingRule(final String id) {
    delete(ACCESS_MAPPING_RULE_INDEX_NAME, id);
  }

  @Override
  public void deleteRole(final String id) {
    delete(ACCESS_ROLE_INDEX_NAME, id);
  }

  private void save(final String index, final String id, final Object document) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, index)
                      .id(id)
                      .document(document)
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not store access entry " + id, e);
    }
  }

  private void delete(final String index, final String id) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d -> d.optimizeIndex(esClient, index).id(id).refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete access entry " + id, e);
    }
  }

  // Check-then-insert is enough for a startup seed; two nodes seeding at once write identical rows.
  private <T> boolean createIfMissing(
      final String index, final String id, final T document, final Class<T> type) {
    try {
      final var existing =
          esClient.get(
              OptimizeGetRequestBuilderES.of(g -> g.optimizeIndex(esClient, index).id(id)), type);
      if (existing.found()) {
        return false;
      }
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, index)
                      .id(id)
                      .document(document)
                      .refresh(Refresh.True)));
      return true;
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not store access entry " + id, e);
    }
  }

  private <T> List<T> findAll(final String index, final Class<T> type) {
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, index)
                          .size(LIST_FETCH_LIMIT)
                          .query(q -> q.matchAll(MatchAllQuery.of(m -> m)))),
              type);
      return response.hits().hits().stream().map(h -> h.source()).toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read access entries from " + index, e);
    }
  }
}
