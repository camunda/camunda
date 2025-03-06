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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.search.filter.RoleFilter;
import io.camunda.search.query.RoleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerRoleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoleServicesTest {

  private RoleServices services;
  private RoleSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    authentication = Authentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(RoleSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new RoleServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
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
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array(), Arrays.array());
    when(client.searchRoles(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var entity = mock(RoleEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array(), Arrays.array());
    when(client.searchRoles(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.findRole(1L);

    // then
    assertThat(searchQueryResult).contains(entity);
  }

  @Test
  public void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchRoles(any())).thenReturn(new SearchQueryResult(0, List.of(), null, null));

    // when / then
    assertThat(services.findRole(key)).isEmpty();
  }

  @Test
  public void shouldUpdateName() {
    // given
    final var roleKey = 100L;
    final var name = "UpdatedName";

    // when
    services.updateRole(roleKey, name);

    // then
    final BrokerRoleUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.UPDATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    assertThat(request.getKey()).isEqualTo(roleKey);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getName()).isEqualTo(name);
  }

  @Test
  public void shouldAddUserToRole() {
    // given
    final var roleKey = 100L;
    final var entityKey = 42;

    // when
    services.addMember(roleKey, EntityType.USER, entityKey);

    // then
    final BrokerRoleEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.ADD_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getRoleKey()).isEqualTo(roleKey);
    assertThat(brokerRequestValue.getEntityKey()).isEqualTo(entityKey);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldRemoveUserFromRole() {
    // given
    final var roleKey = 100L;
    final var entityKey = 42;

    // when
    services.removeMember(roleKey, EntityType.USER, entityKey);

    // then
    final BrokerRoleEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(RoleIntent.REMOVE_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.ROLE);
    final RoleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getRoleKey()).isEqualTo(roleKey);
    assertThat(brokerRequestValue.getEntityKey()).isEqualTo(entityKey);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldGetAllRolesByMemberKey() {
    // given
    final var memberKey = "100L";
    final var roleEntity = mock(RoleEntity.class);
    when(client.findAllRoles(RoleQuery.of(q -> q.filter(f -> f.memberId(memberKey)))))
        .thenReturn(List.of(roleEntity));

    // when
    final var result = services.findAll(RoleQuery.of(q -> q.filter(f -> f.memberId(memberKey))));

    // then
    assertThat(result).isEqualTo(List.of(roleEntity));
  }
}
