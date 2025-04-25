/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ErrorMessages;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupMemberRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupServices extends SearchQueryService<GroupServices, GroupQuery, GroupEntity> {

  private final GroupSearchClient groupSearchClient;

  public GroupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.groupSearchClient = groupSearchClient;
  }

  @Override
  public SearchQueryResult<GroupEntity> search(final GroupQuery query) {
    return groupSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.group().read())))
        .searchGroups(query);
  }

  public List<GroupEntity> findAll(final GroupQuery query) {
    return groupSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.group().read())))
        .findAllGroups(query);
  }

  @Override
  public GroupServices withAuthentication(final Authentication authentication) {
    return new GroupServices(
        brokerClient, securityContextProvider, groupSearchClient, authentication);
  }

  public CompletableFuture<GroupRecord> createGroup(final GroupDTO groupDTO) {
    return sendBrokerRequest(
        new BrokerGroupCreateRequest()
            .setGroupId(groupDTO.groupId)
            .setName(groupDTO.name)
            .setDescription(groupDTO.description));
  }

  public GroupEntity getGroup(final String groupId) {
    return findGroup(groupId)
        .orElseThrow(
            () ->
                new CamundaSearchException(
                    ErrorMessages.ERROR_NOT_FOUND_GROUP_BY_ID.formatted(groupId),
                    CamundaSearchException.Reason.NOT_FOUND));
  }

  public Optional<GroupEntity> findGroupByName(final String name) {
    return search(SearchQueryBuilders.groupSearchQuery().filter(f -> f.name(name)).build())
        .items()
        .stream()
        .findFirst();
  }

  public GroupEntity getGroupByName(final String name) {
    return findGroupByName(name)
        .orElseThrow(
            () ->
                new CamundaSearchException(
                    ErrorMessages.ERROR_NOT_FOUND_GROUP_BY_NAME.formatted(name),
                    CamundaSearchException.Reason.NOT_FOUND));
  }

  public List<GroupEntity> getGroupsByUserKey(final String username) {
    return search(SearchQueryBuilders.groupSearchQuery().filter(f -> f.memberId(username)).build())
        .items()
        .stream()
        .toList();
  }

  public List<GroupEntity> getGroupsByMemberKeys(final Set<String> memberKeys) {
    return findAll(
        SearchQueryBuilders.groupSearchQuery().filter(f -> f.memberIds(memberKeys)).build());
  }

  public Optional<GroupEntity> findGroup(final String groupId) {
    return search(SearchQueryBuilders.groupSearchQuery().filter(f -> f.groupId(groupId)).build())
        .items()
        .stream()
        .findFirst();
  }

  public CompletableFuture<GroupRecord> updateGroup(
      final String groupId, final String name, final String description) {
    return sendBrokerRequest(
        new BrokerGroupUpdateRequest(groupId).setName(name).setDescription(description));
  }

  public CompletableFuture<GroupRecord> deleteGroup(final String groupId) {
    return sendBrokerRequest(new BrokerGroupDeleteRequest(groupId));
  }

  public CompletableFuture<GroupRecord> assignMember(
      final String groupId, final String entityId, final EntityType memberType) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createAddRequest(groupId)
            .setMemberId(entityId)
            .setMemberType(memberType));
  }

  public CompletableFuture<GroupRecord> removeMember(
      final String groupId, final String entityId, final EntityType memberType) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createRemoveRequest(groupId)
            .setMemberId(entityId)
            .setMemberType(memberType));
  }

  public record GroupDTO(String groupId, String name, String description) {}

  public record GroupMemberRequest(String groupId, String entityId, EntityType entityType) {}
}
