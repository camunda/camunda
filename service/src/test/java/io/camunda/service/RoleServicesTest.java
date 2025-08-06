/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.RoleServices.UpdateRoleRequest;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleUpdateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.role.BrokerRoleCreateRequest;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleServicesTest {

  private RoleServices services;
  private RoleSearchClient client;
  private CamundaAuthentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;
  private ApiServicesExecutorProvider executorProvider;

  @BeforeEach
  public void before() {
    authentication = CamundaAuthentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(RoleSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    services =
        new RoleServices(
            stubbedBrokerClient,
            mock(SecurityContextProvider.class),
            client,
            authentication,
            executorProvider);
  }

  @Test
  public void shouldCreateRole() {
    // given
    final var roleId = "roleId";
    final var roleName = "testRole";
    final var description = "description";

    // when
    services.createRole(new CreateRoleRequest(roleId, roleName, description));

    // then
    final BrokerRoleCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    final RoleRecord record = request.getRequestWriter();
    Assertions.assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    Assertions.assertThat(request.getIntent()).isEqualTo(RoleIntent.CREATE);
    assertThat(request.getKey()).isEqualTo(-1L);
    Assertions.assertThat(record).hasRoleId(roleId);
    Assertions.assertThat(record).hasName(roleName);
    Assertions.assertThat(record).hasDescription(description);
  }

  @Test
  public void shouldEmptyQueryReturnRoles() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchRoles(any())).thenReturn(result);

    final RoleFilter filter = new RoleFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.roleSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(RoleEntity.class);
    final var result = new SearchQueryResult<>(1, false, List.of(entity), null, null);
    when(client.searchRoles(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var entity = mock(RoleEntity.class);
    when(entity.roleId()).thenReturn("roleId");
    when(client.getRole(eq("roleId"))).thenReturn(entity);

    // when
    final var searchQueryResult = services.getRole(entity.roleId());

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldUpdateName() {
    // given
    final var roleId = "roleId";
    final var name = "UpdatedName";
    final var description = "UpdatedDescription";

    // when
    services.updateRole(new UpdateRoleRequest(roleId, name, description));

    // then
    final BrokerRoleUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.UPDATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    assertThat(request.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getRoleId()).isEqualTo(roleId);
    assertThat(brokerRequestValue.getName()).isEqualTo(name);
    assertThat(brokerRequestValue.getDescription()).isEqualTo(description);
  }

  @Test
  public void shouldAddUserToRole() {
    // given
    final var roleId = "roleId";
    final var entityId = "entityId";

    // when
    services.addMember(new RoleMemberRequest(roleId, entityId, EntityType.USER));

    // then
    final BrokerRoleEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.ADD_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getRoleId()).isEqualTo(roleId);
    assertThat(brokerRequestValue.getEntityId()).isEqualTo(entityId);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldRemoveUserFromRole() {
    // given
    final var roleId = "100";
    final var username = "42";

    // when
    services.removeMember(new RoleMemberRequest(roleId, username, EntityType.USER));

    // then
    final BrokerRoleEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.REMOVE_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getRoleId()).isEqualTo(roleId);
    assertThat(brokerRequestValue.getEntityId()).isEqualTo(username);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldGetAllRolesByMemberId() {
    // given
    final var memberId = "memberId";
    final var memberType = EntityType.USER;
    final var roleEntity = mock(RoleEntity.class);
    when(client.searchRoles(
            RoleQuery.of(
                q -> q.filter(f -> f.memberId(memberId).childMemberType(memberType)).unlimited())))
        .thenReturn(SearchQueryResult.of(r -> r.items(List.of(roleEntity))));

    // when
    final var result =
        services
            .search(
                RoleQuery.of(
                    q ->
                        q.filter(f -> f.memberId(memberId).childMemberType(memberType))
                            .unlimited()))
            .items();

    // then
    assertThat(result).isEqualTo(List.of(roleEntity));
  }
}
