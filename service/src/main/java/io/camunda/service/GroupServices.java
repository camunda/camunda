/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.GROUP_READ_AUTHORIZATION;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupServices extends SearchQueryService<GroupServices, GroupQuery, GroupEntity> {

  private final GroupSearchClient groupSearchClient;

  public GroupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.groupSearchClient = groupSearchClient;
  }

  public List<GroupEntity> getGroupsByMemberTypeAndMemberIds(
      final Map<EntityType, Set<String>> memberTypesToMemberIds,
      final CamundaAuthentication authentication) {
    return search(
            GroupQuery.of(
                groupQuery ->
                    groupQuery
                        .filter(groupFilter -> groupFilter.memberIdsByType(memberTypesToMemberIds))
                        .unlimited()),
            authentication)
        .items();
  }

  @Override
  public SearchQueryResult<GroupEntity> search(
      final GroupQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GROUP_READ_AUTHORIZATION))
                .searchGroups(query));
  }

  public CompletableFuture<GroupRecord> createGroup(
      final GroupDTO groupDTO, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupCreateRequest()
            .setGroupId(groupDTO.groupId)
            .setName(groupDTO.name)
            .setDescription(groupDTO.description),
        authentication);
  }

  public GroupEntity getGroup(final String groupId, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, withAuthorization(GROUP_READ_AUTHORIZATION, groupId)))
                .getGroup(groupId));
  }

  public CompletableFuture<GroupRecord> updateGroup(
      final String groupId,
      final String name,
      final String description,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupUpdateRequest(groupId).setName(name).setDescription(description),
        authentication);
  }

  public CompletableFuture<GroupRecord> deleteGroup(
      final String groupId, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerGroupDeleteRequest(groupId), authentication);
  }

  public CompletableFuture<GroupRecord> assignMember(
      final GroupMemberDTO groupMemberDTO, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createAddRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(groupMemberDTO.memberType),
        authentication);
  }

  public CompletableFuture<GroupRecord> removeMember(
      final GroupMemberDTO groupMemberDTO, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createRemoveRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(groupMemberDTO.memberType),
        authentication);
  }

  public SearchQueryResult<GroupMemberEntity> searchMembers(
      final GroupMemberQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GROUP_READ_AUTHORIZATION))
                .searchGroupMembers(query));
  }

  public record GroupDTO(String groupId, String name, String description) {}

  public record GroupMemberDTO(String groupId, String memberId, EntityType memberType) {}
}
