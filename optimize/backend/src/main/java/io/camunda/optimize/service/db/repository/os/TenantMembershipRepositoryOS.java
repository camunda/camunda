/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_MEMBERSHIP_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;

import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.db.schema.index.TenantMembershipIndex;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class TenantMembershipRepositoryOS implements TenantMembershipRepository {

  private final OptimizeOpenSearchClient osClient;
  private final OptimizeIndexNameService indexNameService;

  public TenantMembershipRepositoryOS(
      final OptimizeOpenSearchClient osClient, final OptimizeIndexNameService indexNameService) {
    this.osClient = osClient;
    this.indexNameService = indexNameService;
  }

  @Override
  public void assign(final TenantMembershipDto membership) {
    osClient.index(
        new IndexRequest.Builder<TenantMembershipDto>()
            .index(alias())
            .id(membership.getId())
            .document(membership)
            .refresh(Refresh.True));
  }

  @Override
  public void unassign(final String tenantId, final MemberType memberType, final String memberId) {
    osClient.delete(alias(), TenantMembershipDto.idOf(tenantId, memberType, memberId));
  }

  @Override
  public Set<String> findTenantIds(final MemberType memberType, final Set<String> memberIds) {
    if (memberIds.isEmpty()) {
      return Set.of();
    }
    return search(
            and(
                term(TenantMembershipIndex.MEMBER_TYPE, memberType.name()),
                stringTerms(TenantMembershipIndex.MEMBER_ID, memberIds)))
        .stream()
        .map(TenantMembershipDto::getTenantId)
        .collect(Collectors.toSet());
  }

  @Override
  public List<TenantMembershipDto> findByTenant(final String tenantId) {
    return search(term(TenantMembershipIndex.TENANT_ID, tenantId));
  }

  private List<TenantMembershipDto> search(
      final org.opensearch.client.opensearch._types.query_dsl.Query query) {
    return osClient.searchValues(
        new SearchRequest.Builder().index(alias()).query(query).size(LIST_FETCH_LIMIT),
        TenantMembershipDto.class);
  }

  private String alias() {
    return indexNameService.getOptimizeIndexAliasForIndex(TENANT_MEMBERSHIP_INDEX_NAME);
  }
}
