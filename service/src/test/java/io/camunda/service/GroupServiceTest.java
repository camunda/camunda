/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.filter.GroupFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupMemberRequest;
import io.camunda.zeebe.gateway.impl.broker.request.group.BrokerGroupUpdateRequest;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GroupServiceTest {

  private GroupServices services;
  private GroupSearchClient client;
  private CamundaAuthentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;
  private ApiServicesExecutorProvider executorProvider;

  @BeforeEach
  public void before() {
    authentication = CamundaAuthentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(GroupSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    services =
        new GroupServices(
            stubbedBrokerClient,
            mock(SecurityContextProvider.class),
            client,
            authentication,
            executorProvider);
  }

  @Test
  public void shouldCreateGroup() {
    // given
    final var groupId = "groupId";
    final var groupName = "testGroup";
    final var description = "description";

    // when
    final var createGroupRequest = new GroupDTO(groupId, groupName, description);
    services.createGroup(createGroupRequest);

    // then
    final BrokerGroupCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    final GroupRecord record = request.getRequestWriter();
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isEqualTo(GroupIntent.CREATE);
    assertThat(request.getKey()).isEqualTo(-1L);
    assertThat(record).hasName(groupName);
    assertThat(record).hasGroupId(groupId);
    assertThat(record).hasDescription(description);
  }

  @Test
  public void shouldEmptyQueryReturnGroups() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchGroups(any())).thenReturn(result);

    final GroupFilter filter = new GroupFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.groupSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleGroup() {
    // given
    final var entity = mock(GroupEntity.class);
    final var result =
        new SearchQueryResult.Builder<GroupEntity>().total(1).items(List.of(entity)).build();
    when(client.searchGroups(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleGroupForGet() {
    // given
    final var entity = mock(GroupEntity.class);
    when(client.getGroup(any())).thenReturn(entity);

    // when
    final var searchQueryResult = services.getGroup("groupId");

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldUpdateGroup() {
    // given
    final var groupKey = Protocol.encodePartitionId(1, 100L);
    final var groupId = String.valueOf(groupKey);
    final var name = "UpdatedName";
    final var description = "UpdatedDescription";

    // when
    services.updateGroup(groupId, name, description);

    // then
    final BrokerGroupUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.UPDATE);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasName(name);
    assertThat(record).hasGroupId(groupId);
    assertThat(record).hasDescription(description);
  }

  @Test
  public void shouldDeleteGroup() {
    // given
    final var groupKey = Protocol.encodePartitionId(1, 123L);
    final var groupId = String.valueOf(groupKey);

    // when
    services.deleteGroup(groupId);

    // then
    final BrokerGroupDeleteRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.DELETE);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasGroupId(groupId);
  }

  @Test
  public void shouldAddMemberToGroup() {
    // given
    final var groupId = UUID.randomUUID().toString();
    final var memberId = "456";
    final var dto = new GroupMemberDTO(groupId, memberId, EntityType.USER);

    // when
    services.assignMember(dto);

    // then
    final BrokerGroupMemberRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.ADD_ENTITY);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasGroupId(groupId);
    assertThat(record).hasEntityId(memberId);
    assertThat(record).hasEntityType(EntityType.USER);
  }

  @Test
  public void shouldRemoveMemberFromGroup() {
    // given
    final var groupId = UUID.randomUUID().toString();
    final var username = "username";
    final var memberType = EntityType.USER;
    final var dto = new GroupMemberDTO(groupId, username, EntityType.USER);

    // when
    services.removeMember(dto);

    // then
    final BrokerGroupMemberRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(request.getValueType()).isEqualTo(ValueType.GROUP);
    assertThat(request.getIntent()).isNotEvent().isEqualTo(GroupIntent.REMOVE_ENTITY);
    final GroupRecord record = request.getRequestWriter();
    assertThat(record).hasGroupId(groupId);
    assertThat(record).hasEntityId(username);
    assertThat(record).hasEntityType(EntityType.USER);
  }
}
