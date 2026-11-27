/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import static io.camunda.optimize.service.db.DatabaseConstants.LIST_FETCH_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.ROLE_INDEX_NAME;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_CHILD;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.JOIN_PARENT;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_ID;
import static io.camunda.optimize.service.db.schema.index.RoleIndex.MEMBER_TYPE;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.optimize.dto.optimize.access.RoleDto;
import io.camunda.optimize.dto.optimize.access.RoleMemberDto;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.es.builders.OptimizeDeleteRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeGetRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeIndexRequestBuilderES;
import io.camunda.optimize.service.db.es.builders.OptimizeSearchRequestBuilderES;
import io.camunda.optimize.service.db.es.reader.ElasticsearchReaderUtil;
import io.camunda.optimize.service.db.repository.RoleRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import io.camunda.security.api.model.authz.EntityType;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
@SuppressWarnings({"rawtypes", "unchecked"})
public class RoleRepositoryES implements RoleRepository {

  private final OptimizeElasticsearchClient esClient;
  private final TaskRepositoryES taskRepositoryES;
  private final ConfigurationService configurationService;
  private final ObjectMapper objectMapper;

  public RoleRepositoryES(
      final OptimizeElasticsearchClient esClient,
      final TaskRepositoryES taskRepositoryES,
      final ConfigurationService configurationService,
      final ObjectMapper objectMapper) {
    this.esClient = esClient;
    this.taskRepositoryES = taskRepositoryES;
    this.configurationService = configurationService;
    this.objectMapper = objectMapper;
  }

  @Override
  public Optional<RoleDto> get(final String roleId) {
    try {
      final GetResponse<Map> response =
          esClient.get(
              OptimizeGetRequestBuilderES.of(
                  g -> g.optimizeIndex(esClient, ROLE_INDEX_NAME).id(RoleDto.documentId(roleId))),
              Map.class);
      return response.found()
          ? Optional.ofNullable(response.source()).map(RoleDto::fromDocument)
          : Optional.empty();
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read role " + roleId, e);
    }
  }

  @Override
  public List<RoleDto> getAll() {
    return searchAll(Query.of(q -> q.term(t -> t.field(JOIN).value(JOIN_PARENT))), "roles").stream()
        .map(RoleDto::fromDocument)
        .toList();
  }

  @Override
  public void upsert(final RoleDto role) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, ROLE_INDEX_NAME)
                      .id(RoleDto.documentId(role.roleId()))
                      .refresh(Refresh.True)
                      .document(role.toDocument())));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not persist role " + role.roleId(), e);
    }
  }

  @Override
  public void delete(final String roleId) {
    // abort on a concurrent member write, so the role is never deleted with a member left behind
    taskRepositoryES.tryDeleteByQueryRequest(
        membersOf(roleId), "members of role " + roleId, true, true, ROLE_INDEX_NAME);
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, ROLE_INDEX_NAME)
                      .id(RoleDto.documentId(roleId))
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not delete role " + roleId, e);
    }
  }

  @Override
  public List<RoleMemberDto> getMembers(final String roleId) {
    return searchAll(membersOf(roleId), "members of role " + roleId).stream()
        .map(RoleMemberDto::fromDocument)
        .toList();
  }

  @Override
  public void addMember(final String roleId, final RoleMemberDto member) {
    try {
      esClient.index(
          OptimizeIndexRequestBuilderES.of(
              b ->
                  b.optimizeIndex(esClient, ROLE_INDEX_NAME)
                      .id(member.documentId(roleId))
                      // a join child must live on its parent's shard
                      .routing(RoleDto.documentId(roleId))
                      .refresh(Refresh.True)
                      .document(member.toDocument(roleId))));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Could not add member " + member.memberId() + " to role " + roleId, e);
    }
  }

  @Override
  public void removeMember(final String roleId, final RoleMemberDto member) {
    try {
      esClient.delete(
          OptimizeDeleteRequestBuilderES.of(
              d ->
                  d.optimizeIndex(esClient, ROLE_INDEX_NAME)
                      .id(member.documentId(roleId))
                      .routing(RoleDto.documentId(roleId))
                      .refresh(Refresh.True)));
    } catch (final IOException e) {
      throw new OptimizeRuntimeException(
          "Could not remove member " + member.memberId() + " from role " + roleId, e);
    }
  }

  @Override
  public Set<String> getRoleIdsOfMembers(
      final EntityType memberType, final Collection<String> memberIds) {
    if (memberIds.isEmpty()) {
      return Set.of();
    }
    final List<FieldValue> ids = memberIds.stream().map(FieldValue::of).toList();
    final Query query =
        Query.of(
            q ->
                q.hasChild(
                    h ->
                        h.type(JOIN_CHILD)
                            .query(
                                c ->
                                    c.bool(
                                        b ->
                                            b.filter(
                                                    f ->
                                                        f.term(
                                                            t ->
                                                                t.field(MEMBER_TYPE)
                                                                    .value(memberType.name())))
                                                .filter(
                                                    f ->
                                                        f.terms(
                                                            t ->
                                                                t.field(MEMBER_ID)
                                                                    .terms(v -> v.value(ids))))))));
    return searchAll(query, "roles of " + memberType + " members").stream()
        .map(RoleDto::fromDocument)
        .map(RoleDto::roleId)
        .collect(Collectors.toSet());
  }

  private static Query membersOf(final String roleId) {
    return Query.of(q -> q.parentId(p -> p.type(JOIN_CHILD).id(RoleDto.documentId(roleId))));
  }

  private List<Map<String, Object>> searchAll(final Query query, final String description) {
    final int scrollTimeout =
        configurationService.getElasticSearchConfiguration().getScrollTimeoutInSeconds();
    final SearchResponse<Map> response;
    try {
      response =
          esClient.search(
              OptimizeSearchRequestBuilderES.of(
                  s ->
                      s.optimizeIndex(esClient, ROLE_INDEX_NAME)
                          .query(query)
                          .size(LIST_FETCH_LIMIT)
                          .scroll(Time.of(t -> t.time(scrollTimeout + "s")))),
              Map.class);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Could not read " + description, e);
    }
    return (List<Map<String, Object>>)
        (List)
            ElasticsearchReaderUtil.retrieveAllScrollResults(
                response, Map.class, objectMapper, esClient, scrollTimeout);
  }
}
