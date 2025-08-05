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
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
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
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.groupSearchClient = groupSearchClient;
  }

  public List<GroupEntity> getGroupsByMemberTypeAndMemberIds(
      final Map<EntityType, Set<String>> memberTypesToMemberIds) {
    return search(
            GroupQuery.of(
                groupQuery ->
                    groupQuery
                        .filter(groupFilter -> groupFilter.memberIdsByType(memberTypesToMemberIds))
                        .unlimited()))
        .items();
  }

  @Override
  public SearchQueryResult<GroupEntity> search(final GroupQuery query) {
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, GROUP_READ_AUTHORIZATION))
                .searchGroups(query));
  }

  @Override
  public GroupServices withAuthentication(final CamundaAuthentication authentication) {
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
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, withAuthorization(GROUP_READ_AUTHORIZATION, groupId)))
                .getGroup(groupId));
  }

  public CompletableFuture<GroupRecord> updateGroup(
      final String groupId, final String name, final String description) {
    return sendBrokerRequest(
        new BrokerGroupUpdateRequest(groupId).setName(name).setDescription(description));
  }

  public CompletableFuture<GroupRecord> deleteGroup(final String groupId) {
    return sendBrokerRequest(new BrokerGroupDeleteRequest(groupId));
  }

  public CompletableFuture<GroupRecord> assignMember(final GroupMemberDTO groupMemberDTO) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createAddRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(groupMemberDTO.memberType));
  }

  public CompletableFuture<GroupRecord> removeMember(final GroupMemberDTO groupMemberDTO) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createRemoveRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(groupMemberDTO.memberType));
  }

  public SearchQueryResult<GroupMemberEntity> searchMembers(final GroupQuery query) {
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
