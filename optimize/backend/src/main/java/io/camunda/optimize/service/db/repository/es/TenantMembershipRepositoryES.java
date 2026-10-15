/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.TENANT_MEMBERSHIP_INDEX_NAME;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto;
import io.camunda.optimize.dto.optimize.query.TenantMembershipDto.MemberType;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.repository.TenantMembershipRepository;
import io.camunda.optimize.service.db.schema.index.TenantMembershipIndex;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TenantMembershipRepositoryES implements TenantMembershipRepository {

  private final OptimizeElasticsearchClient esClient;

  public TenantMembershipRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public void assign(final TenantMembershipDto membership) {
    try {
      // Refresh so an admin's change is visible to the very next membership lookup.
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, TENANT_MEMBERSHIP_INDEX_NAME)
                      .id(membership.getId())
                      .document(membership)
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not assign tenant " + membership.getId(), e);
    }
  }

  @Override
  public void unassign(final String tenantId, final MemberType memberType, final String memberId) {
    final String id = TenantMembershipDto.idOf(tenantId, memberType, memberId);
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, TENANT_MEMBERSHIP_INDEX_NAME)
                      .id(id)
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not unassign tenant " + id, e);
    }
  }

  @Override
  public Set<String> findTenantIds(final MemberType memberType, final Set<String> memberIds) {
    if (memberIds.isEmpty()) {
      return Set.of();
    }
    final List<FieldValue> values = memberIds.stream().map(FieldValue::of).toList();
    final Query query =
        Query.of(
            q ->
                q.bool(
                    b ->
                        b.filter(
                                f ->
                                    f.term(
                                        t ->
                                            t.field(TenantMembershipIndex.MEMBER_TYPE)
                                                .value(memberType.name())))
                            .filter(
                                f ->
                                    f.terms(
                                        t ->
                                            t.field(TenantMembershipIndex.MEMBER_ID)
                                                .terms(tt -> tt.value(values))))));
    return search(query).stream().map(TenantMembershipDto::getTenantId).collect(Collectors.toSet());
  }

  @Override
  public List<TenantMembershipDto> findByTenant(final String tenantId) {
    return search(
        Query.of(q -> q.term(t -> t.field(TenantMembershipIndex.TENANT_ID).value(tenantId))));
  }

  private List<TenantMembershipDto> search(final Query query) {
    try {
      final var response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  b ->
                      b.optimizeIndex(esClient, TENANT_MEMBERSHIP_INDEX_NAME)
                          .size(LIST_FETCH_LIMIT)
                          .query(query)),
              TenantMembershipDto.class);
      return response.hits().hits().stream().map(h -> h.source()).toList();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read tenant memberships", e);
    }
  }
}
