/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.ACCESS_MAPPING_RULE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ACCESS_ROLE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;
import static java.lang.String.format;

import io.camunda.optimize.dto.optimize.query.AccessMappingRuleDto;
import io.camunda.optimize.dto.optimize.query.AccessRoleDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.AccessRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class AccessRepositoryOS implements AccessRepository {

  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public AccessRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
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

  private <T> void save(final String index, final String id, final T document) {
    osClient.index(
        new IndexRequest.Builder<T>()
            .index(indexNameService.getOptimizeIndexAliasForIndex(index))
            .id(id)
            .document(document)
            .refresh(Refresh.True));
  }

  private void delete(final String index, final String id) {
    osClient.delete(indexNameService.getOptimizeIndexAliasForIndex(index), id);
  }

  // Check-then-insert is enough for a startup seed; two nodes seeding at once write identical rows.
  private <T> boolean createIfMissing(
      final String index, final String id, final T document, final Class<T> type) {
    final String alias = indexNameService.getOptimizeIndexAliasForIndex(index);
    final var existing =
        osClient.get(
            new GetRequest.Builder().index(alias).id(id),
            type,
            format("Could not read access entry %s", id));
    if (existing.found()) {
      return false;
    }
    osClient.index(
        new IndexRequest.Builder<T>().index(alias).id(id).document(document).refresh(Refresh.True));
    return true;
  }

  private <T> List<T> findAll(final String index, final Class<T> type) {
    return osClient.searchValues(
        new SearchRequest.Builder()
            .index(indexNameService.getOptimizeIndexAliasForIndex(index))
            .query(matchAll())
            .size(LIST_FETCH_LIMIT),
        type);
  }
}
