/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.GROUP_READ_AUTHORIZATION;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.GroupMemberEntity;
import io.camunda.search.query.GroupMemberQuery;
import io.camunda.search.query.GroupQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.EntityType;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupMemberRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupSetEntitiesRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.mapper.AuthzModelMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupServices extends SearchQueryService<GroupServices, GroupQuery, GroupEntity> {

  private final GroupSearchClient groupSearchClient;

  public GroupServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
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
            .setDescription(groupDTO.description)
            .setExternalId(groupDTO.externalId)
            .setAttributes(groupDTO.attributes)
            .setSource(groupDTO.source),
        authentication);
  }

  public GroupEntity getGroup(final String groupId, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            groupSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withRequiredAuthorization(GROUP_READ_AUTHORIZATION, groupId)))
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

  /**
   * Updates only the given attributes of the group; all other attributes keep their current value.
   */
  public CompletableFuture<GroupRecord> updateGroup(
      final GroupDTO groupDTO,
      final Set<GroupAttribute> changedAttributes,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupUpdateRequest(groupDTO.groupId)
            .setName(groupDTO.name)
            .setDescription(groupDTO.description)
            .setExternalId(groupDTO.externalId)
            .setAttributes(groupDTO.attributes)
            .setSource(groupDTO.source)
            .setChangedAttributes(
                changedAttributes.stream()
                    .map(attribute -> GroupRecord.GroupAttribute.valueOf(attribute.name()))
                    .toList()),
        authentication);
  }

  public CompletableFuture<GroupRecord> deleteGroup(
      final String groupId, final CamundaAuthentication authentication) {
    return sendBrokerRequest(new BrokerGroupDeleteRequest(groupId), authentication);
  }

  /**
   * Deletes a group on behalf of a SCIM source: a group owned by another source does not exist for
   * the source, and an unowned group is only deleted if {@code adoptUnowned} is set.
   */
  public CompletableFuture<GroupRecord> deleteGroup(
      final String groupId,
      final String source,
      final boolean adoptUnowned,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupDeleteRequest(groupId)
            .setSource(source)
            .setChangedAttributes(adoption(adoptUnowned)),
        authentication);
  }

  private static List<GroupRecord.GroupAttribute> adoption(final boolean adoptUnowned) {
    return adoptUnowned ? List.of(GroupRecord.GroupAttribute.SOURCE) : List.of();
  }

  public CompletableFuture<GroupRecord> assignMember(
      final GroupMemberDTO groupMemberDTO, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createAddRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(AuthzModelMapper.toProtocol(groupMemberDTO.memberType)),
        authentication);
  }

  /**
   * Assigns a member on behalf of a SCIM source: a member user must be owned by the same source,
   * and an unowned group is adopted if {@code adoptUnowned} is set.
   */
  public CompletableFuture<GroupRecord> assignMember(
      final GroupMemberDTO groupMemberDTO,
      final String source,
      final boolean adoptUnowned,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createAddRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(AuthzModelMapper.toProtocol(groupMemberDTO.memberType))
            .setSource(source)
            .setChangedAttributes(adoption(adoptUnowned)),
        authentication);
  }

  /** Removes a member on behalf of a SCIM source, see {@link #assignMember}. */
  public CompletableFuture<GroupRecord> removeMember(
      final GroupMemberDTO groupMemberDTO,
      final String source,
      final boolean adoptUnowned,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createRemoveRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(AuthzModelMapper.toProtocol(groupMemberDTO.memberType))
            .setSource(source)
            .setChangedAttributes(adoption(adoptUnowned)),
        authentication);
  }

  public CompletableFuture<GroupRecord> removeMember(
      final GroupMemberDTO groupMemberDTO, final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        BrokerGroupMemberRequest.createRemoveRequest(groupMemberDTO.groupId)
            .setMemberId(groupMemberDTO.memberId)
            .setMemberType(AuthzModelMapper.toProtocol(groupMemberDTO.memberType)),
        authentication);
  }

  /**
   * Replaces the complete membership of the given type with {@code memberIds}; the engine computes
   * the difference against its own state. The full list travels in a single command, so it is
   * bounded by the maximum message size.
   */
  public CompletableFuture<GroupRecord> setMembers(
      final String groupId,
      final EntityType memberType,
      final Collection<String> memberIds,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupSetEntitiesRequest(groupId)
            .setEntityType(AuthzModelMapper.toProtocol(memberType))
            .setEntityIds(memberIds),
        authentication);
  }

  /** Replaces the membership on behalf of a SCIM source, see {@link #assignMember}. */
  public CompletableFuture<GroupRecord> setMembers(
      final String groupId,
      final EntityType memberType,
      final Collection<String> memberIds,
      final String source,
      final boolean adoptUnowned,
      final CamundaAuthentication authentication) {
    return sendBrokerRequest(
        new BrokerGroupSetEntitiesRequest(groupId)
            .setEntityType(AuthzModelMapper.toProtocol(memberType))
            .setEntityIds(memberIds)
            .setSource(source)
            .setChangedAttributes(adoption(adoptUnowned)),
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

  public enum GroupAttribute {
    NAME,
    DESCRIPTION,
    EXTERNAL_ID,
    ATTRIBUTES,
    ATTRIBUTES_MERGE,
    /** Adopts an unowned group by setting the source of the request. */
    SOURCE
  }

  public record GroupDTO(
      String groupId,
      String name,
      String description,
      String externalId,
      Map<String, Object> attributes,
      String source) {

    public GroupDTO(
        final String groupId,
        final String name,
        final String description,
        final String externalId,
        final Map<String, Object> attributes) {
      this(groupId, name, description, externalId, attributes, "");
    }

    public GroupDTO(
        final String groupId,
        final String name,
        final String description,
        final String externalId) {
      this(groupId, name, description, externalId, Map.of(), "");
    }

    public GroupDTO(final String groupId, final String name, final String description) {
      this(groupId, name, description, "", Map.of(), "");
    }
  }

  public record GroupMemberDTO(String groupId, String memberId, EntityType memberType) {}
}
