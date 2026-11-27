/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.ROLE_INDEX_NAME;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.and;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.stringTerms;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.term;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_CHILD;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_PARENT;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_ID;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_TYPE;

import io.camunda.optimize.dto.optimize.access.RoleDto;
import io.camunda.optimize.dto.optimize.access.RoleMemberDto;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.repository.RoleRepository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.security.api.model.authz.EntityType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.query_dsl.Query;
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
public class RoleRepositoryOS implements RoleRepository {

  private final OptimizeOpenSearchClient osClient;

  public RoleRepositoryOS(final OptimizeOpenSearchClient osClient) {
    this.osClient = osClient;
  }

  @Override
  public Optional<RoleDto> get(final String roleId) {
    final GetResponse<Map> response =
        osClient.get(
            new GetRequest.Builder().index(ROLE_INDEX_NAME).id(RoleDto.documentId(roleId)),
            Map.class,
            "Could not read role " + roleId);
    return response.found()
        ? Optional.ofNullable(response.source()).map(RoleDto::fromDocument)
        : Optional.empty();
  }

  @Override
  public List<RoleDto> getAll() {
    return searchAll(term(JOIN, JOIN_PARENT)).stream().map(RoleDto::fromDocument).toList();
  }

  @Override
  public void upsert(final RoleDto role) {
    osClient.index(
        new IndexRequest.Builder<Map<String, Object>>()
            .index(ROLE_INDEX_NAME)
            .id(RoleDto.documentId(role.roleId()))
            .refresh(Refresh.True)
            .document(role.toDocument()));
  }

  @Override
  public void delete(final String roleId) {
    osClient.deleteByQuery(membersOf(roleId), true, ROLE_INDEX_NAME);
    osClient.delete(
        new DeleteRequest.Builder()
            .index(ROLE_INDEX_NAME)
            .id(RoleDto.documentId(roleId))
            .refresh(Refresh.True),
        "Could not delete role " + roleId);
  }

  @Override
  public List<RoleMemberDto> getMembers(final String roleId) {
    return searchAll(membersOf(roleId)).stream().map(RoleMemberDto::fromDocument).toList();
  }

  @Override
  public void addMember(final String roleId, final RoleMemberDto member) {
    osClient.index(
        new IndexRequest.Builder<Map<String, Object>>()
            .index(ROLE_INDEX_NAME)
            .id(member.documentId(roleId))
            // a join child must live on its parent's shard
            .routing(RoleDto.documentId(roleId))
            .refresh(Refresh.True)
            .document(member.toDocument(roleId)));
  }

  @Override
  public void removeMember(final String roleId, final RoleMemberDto member) {
    osClient.delete(
        new DeleteRequest.Builder()
            .index(ROLE_INDEX_NAME)
            .id(member.documentId(roleId))
            .routing(RoleDto.documentId(roleId))
            .refresh(Refresh.True),
        "Could not remove member " + member.memberId() + " from role " + roleId);
  }

  @Override
  public Set<String> getRoleIdsOfMembers(
      final EntityType memberType, final Collection<String> memberIds) {
    if (memberIds.isEmpty()) {
      return Set.of();
    }
    final Query membersQuery =
        and(term(MEMBER_TYPE, memberType.name()), stringTerms(MEMBER_ID, memberIds));
    final Query query = Query.of(q -> q.hasChild(h -> h.type(JOIN_CHILD).query(membersQuery)));
    return searchAll(query).stream()
        .map(RoleDto::fromDocument)
        .map(RoleDto::roleId)
        .collect(Collectors.toSet());
  }

  private static Query membersOf(final String roleId) {
    return Query.of(q -> q.parentId(p -> p.type(JOIN_CHILD).id(RoleDto.documentId(roleId))));
  }

  private List<Map<String, Object>> searchAll(final Query query) {
    return (List<Map<String, Object>>)
        (List)
            osClient.scrollValues(
                new SearchRequest.Builder()
                    .index(ROLE_INDEX_NAME)
                    .query(query)
                    .size(LIST_FETCH_LIMIT),
                Map.class);
  }
}
