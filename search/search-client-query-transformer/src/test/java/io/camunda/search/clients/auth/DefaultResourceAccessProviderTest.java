/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.AuthorizationScope.AuthorizationScopeFactory;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.impl.AuthorizationChecker;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultResourceAccessProviderTest {

  @Mock private AuthorizationChecker authorizationChecker;
  private DefaultResourceAccessProvider resourceAccessProvider;

  @BeforeEach
  void setUp() {
    resourceAccessProvider = new DefaultResourceAccessProvider(authorizationChecker);
  }

  @Test
  void shouldAllowAccessToResourceIds() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());

    when(authorizationChecker.retrieveAuthorizedResourceKeys(any()))
        .thenReturn(
            List.of(AuthorizationScopeFactory.id("bar"), AuthorizationScopeFactory.id("baz")));

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourceType()).isEqualTo(authorization.resourceType());
    assertThat(result.authorization().permissionType()).isEqualTo(authorization.permissionType());
    assertThat(result.authorization().resourceIds()).containsExactlyInAnyOrder("bar", "baz");
  }

  @Test
  void shouldAllowWildcardResourceAccess() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());

    when(authorizationChecker.retrieveAuthorizedResourceKeys(any()))
        .thenReturn(List.of(AuthorizationScopeFactory.wildcard()));

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isTrue();
    assertThat(result.authorization().resourceType()).isEqualTo(authorization.resourceType());
    assertThat(result.authorization().permissionType()).isEqualTo(authorization.permissionType());
    assertThat(result.authorization().resourceIds()).containsExactlyInAnyOrder("*");
  }

  @Test
  void shouldDenyResourceAccessWhenNoResourceIdsRetrieved() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());

    when(authorizationChecker.retrieveAuthorizedResourceKeys(any())).thenReturn(List.of());

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }
}
