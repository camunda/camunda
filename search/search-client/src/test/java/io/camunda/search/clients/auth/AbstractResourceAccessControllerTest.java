/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.condition.AuthorizationConditions;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AbstractResourceAccessControllerTest {

  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantAccessProvider tenantAccessProvider;

  private ResourceAccessController controller;

  @BeforeEach
  void setUp() {
    controller = new DummyResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }

  @Nested
  class WithConditionalAuthorizations {

    @Test
    void withSingleAuthorization() {
      // given
      final var resource = new Object();
      final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
      final var authorization =
          Authorization.of(a -> a.processDefinition().readProcessDefinition())
              .withCondition(o -> false);

      final var securityContext =
          SecurityContext.of(
              s -> s.withAuthentication(authentication).withAuthorization(authorization));

      when(tenantAccessProvider.hasTenantAccess(authentication, resource))
          .thenReturn(TenantAccess.allowed(List.of("baz")));

      // then
      assertThatThrownBy(() -> controller.doGet(securityContext, a -> resource))
          .isInstanceOf(ResourceAccessDeniedException.class)
          .hasMessageContaining("not applicable");
      verify(resourceAccessProvider, never()).hasResourceAccess(any(), any(), any());
    }

    @Test
    void withAnyOfAuthorization() {
      // given
      final var resource = new Object();
      final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
      final var enabledAuthorization = Authorization.of(a -> a.processDefinition().readUserTask());
      final var disabledAuthorization =
          Authorization.of(a -> a.processDefinition().readProcessDefinition())
              .withCondition(o -> false);

      // it's important to have the disabled one first, otherwise the controller would skip checking
      final var securityContext =
          SecurityContext.of(
              s ->
                  s.withAuthentication(authentication)
                      .withAuthorizationCondition(
                          AuthorizationConditions.anyOf(
                              disabledAuthorization, enabledAuthorization)));

      when(resourceAccessProvider.hasResourceAccess(authentication, enabledAuthorization, resource))
          .thenReturn(ResourceAccess.allowed(enabledAuthorization));
      when(tenantAccessProvider.hasTenantAccess(authentication, resource))
          .thenReturn(TenantAccess.allowed(List.of("baz")));

      // when
      controller.doGet(securityContext, a -> resource);

      // then
      verify(resourceAccessProvider, times(1))
          .hasResourceAccess(authentication, enabledAuthorization, resource);
    }
  }

  @Nested
  class WithTransitiveAuthorizations {

    @Test
    void withSingleAuthorization() {
      // given
      final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
      final var authorization =
          Authorization.of(a -> a.processDefinition().readProcessDefinition().transitive());

      final var securityContext =
          SecurityContext.of(
              s -> s.withAuthentication(authentication).withAuthorization(authorization));
      when(resourceAccessProvider.resolveResourceAccess(authentication, authorization))
          .thenReturn(ResourceAccess.wildcard(authorization));
      when(tenantAccessProvider.resolveTenantAccess(authentication))
          .thenReturn(TenantAccess.allowed(List.of("bar")));

      // when
      final var reference = new AtomicReference<ResourceAccessChecks>();
      controller.doSearch(
          securityContext,
          a -> {
            reference.set(a);
            return null;
          });

      // then
      final var result = reference.get();
      assertThat(result.authorizationCheck().enabled()).isTrue();
      assertThat(result.authorizationCheck().authorizationCondition())
          .isEqualTo(AuthorizationConditions.single(authorization));
    }

    @Test
    void withAnyOfAuthorization() {
      // given
      final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
      final var authorization =
          Authorization.of(a -> a.processDefinition().readProcessDefinition().transitive());

      final var securityContext =
          SecurityContext.of(
              s ->
                  s.withAuthentication(authentication)
                      .withAuthorizationCondition(AuthorizationConditions.anyOf(authorization)));
      when(resourceAccessProvider.resolveResourceAccess(authentication, authorization))
          .thenReturn(ResourceAccess.wildcard(authorization));
      when(tenantAccessProvider.resolveTenantAccess(authentication))
          .thenReturn(TenantAccess.allowed(List.of("bar")));

      // when
      final var reference = new AtomicReference<ResourceAccessChecks>();
      controller.doSearch(
          securityContext,
          a -> {
            reference.set(a);
            return null;
          });

      // then
      final var result = reference.get();
      assertThat(result.authorizationCheck().enabled()).isTrue();
      assertThat(result.authorizationCheck().authorizationCondition())
          .isEqualTo(AuthorizationConditions.anyOf(authorization));
    }
  }

  private class DummyResourceAccessController extends AbstractResourceAccessController {

    ResourceAccessProvider resourceAccessProvider;
    TenantAccessProvider tenantAccessProvider;

    public DummyResourceAccessController(
        final ResourceAccessProvider resourceAccessProvider,
        final TenantAccessProvider tenantAccessProvider) {
      this.resourceAccessProvider = resourceAccessProvider;
      this.tenantAccessProvider = tenantAccessProvider;
    }

    @Override
    protected ResourceAccessProvider getResourceAccessProvider() {
      return resourceAccessProvider;
    }

    @Override
    protected TenantAccessProvider getTenantAccessProvider() {
      return tenantAccessProvider;
    }
  }
}
