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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserSearchClient;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserDeleteRequest;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class UserServiceTest {

  ArgumentCaptor<BrokerUserDeleteRequest> userDeleteRequestArgumentCaptor;
  private UserServices services;
  private UserSearchClient client;
  private BrokerClient brokerClient;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    brokerClient = mock(BrokerClient.class);
    authentication = mock(Authentication.class);
    userDeleteRequestArgumentCaptor = ArgumentCaptor.forClass(BrokerUserDeleteRequest.class);
    services =
        new UserServices(brokerClient, mock(SecurityContextProvider.class), client, authentication);
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
    when(authentication.token()).thenReturn("token");
    final var userRecord = new UserRecord();
    userRecord.setUserKey(1234L);
    when(brokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(userRecord)));

    services.deleteUser(1234L);

    verify(brokerClient).sendRequest(userDeleteRequestArgumentCaptor.capture());
    final var request = userDeleteRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getUserKey()).isEqualTo(1234L);
    assertThat(request.getRequestWriter().getUsername()).isEmpty();
  }
}