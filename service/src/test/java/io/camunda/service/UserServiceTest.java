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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

public class UserServiceTest {

  ArgumentCaptor<BrokerUserDeleteRequest> userDeleteRequestArgumentCaptor;
  private UserServices services;
  private UserSearchClient client;
  private BrokerClient brokerClient;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    brokerClient = mock(BrokerClient.class);
    authentication = mock(CamundaAuthentication.class);
    final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    userDeleteRequestArgumentCaptor = ArgumentCaptor.forClass(BrokerUserDeleteRequest.class);
    final ApiServicesExecutorProvider executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    services =
        new UserServices(
            brokerClient,
            mock(SecurityContextProvider.class),
            client,
            authentication,
            passwordEncoder,
            executorProvider);
  }

  @Test
  public void shouldEmptyQueryReturnUsers() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchUsers(any())).thenReturn(result);

    final UserFilter filter = new UserFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.userSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldTriggerDeleteRequest() {
    // when
    when(authentication.claims()).thenReturn(Map.of());
    final var userRecord = new UserRecord();
    final var username = "test";
    userRecord.setUsername(username);
    when(brokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(userRecord)));

    services.deleteUser(username);

    verify(brokerClient).sendRequest(userDeleteRequestArgumentCaptor.capture());
    final var request = userDeleteRequestArgumentCaptor.getValue().getRequestWriter();
    assertThat(request.getUsername()).isEqualTo(username);
    assertThat(request.getUserKey()).isEqualTo(-1L);
  }

  @Test
  public void shouldReturnUserForGet() {
    // given
    final var entity = mock(UserEntity.class);
    when(entity.username()).thenReturn("test");
    when(client.getUser(eq("test"))).thenReturn(entity);

    // when
    final var searchQueryResult = services.getUser(entity.username());

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }
}
