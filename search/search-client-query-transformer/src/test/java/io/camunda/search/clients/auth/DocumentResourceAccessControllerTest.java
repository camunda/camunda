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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.TenantOwnedEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.exception.TenantAccessDeniedException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.reader.ResourceAccess;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.security.reader.ResourceAccessController;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentResourceAccessControllerTest {

  @Mock private ResourceAccessProvider resourceAccessProvider;
  @Mock private TenantAccessProvider tenantAccessProvider;
  private ResourceAccessController controller;

  @BeforeEach
  public void setup() {
    controller =
        new DocumentBasedResourceAccessController(resourceAccessProvider, tenantAccessProvider);
  }

  @Test
  void shouldEnableAuthorizationCheck() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    final var requiredAuthorization =
        Authorization.of(
            a -> a.processDefinition().readProcessDefinition().resourceIds(List.of("bar", "baz")));
    when(resourceAccessProvider.resolveResourceAccess(eq(authentication), eq(authorization)))
        .thenReturn(ResourceAccess.allowed(requiredAuthorization));
    when(tenantAccessProvider.resolveTenantAccess(any())).thenReturn(TenantAccess.wildcard(null));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.authorizationCheck().enabled()).isTrue();

    final var authorizationToBeChecked = result.authorizationCheck().authorization();
    assertThat(authorizationToBeChecked).isEqualTo(requiredAuthorization);
  }

  @Test
  void shouldDisableAuthorizationCheckWithWildcardResourceAccess() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    final var requiredAuthorization =
        Authorization.of(
            a -> a.processDefinition().readProcessDefinition().resourceIds(List.of("*")));
    when(resourceAccessProvider.resolveResourceAccess(eq(authentication), eq(authorization)))
        .thenReturn(ResourceAccess.wildcard(requiredAuthorization));
    when(tenantAccessProvider.resolveTenantAccess(any())).thenReturn(TenantAccess.wildcard(null));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.authorizationCheck().enabled()).isFalse();
    assertThat(result.authorizationCheck().authorization()).isNull();
  }

  @Test
  void shouldEnableResourceAccessCheckEvenWhenNoResourceIdsProvided() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    final var requiredAuthorization =
        Authorization.of(a -> a.processDefinition().readProcessDefinition());

    when(resourceAccessProvider.resolveResourceAccess(eq(authentication), eq(authorization)))
        .thenReturn(ResourceAccess.denied(requiredAuthorization));
    when(tenantAccessProvider.resolveTenantAccess(any())).thenReturn(TenantAccess.wildcard(null));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.authorizationCheck().enabled()).isTrue();
    assertThat(result.authorizationCheck().authorization()).isEqualTo(requiredAuthorization);
  }

  @Test
  void shouldEnableTenantCheck() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(null));
    when(tenantAccessProvider.resolveTenantAccess(eq(authentication)))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.tenantCheck().enabled()).isTrue();
    assertThat(result.tenantCheck().tenantIds()).containsExactlyInAnyOrder("bar");
  }

  @Test
  void shouldEnableTenantCheckEvenWhenTenantAccessDenied() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(null));
    when(tenantAccessProvider.resolveTenantAccess(eq(authentication)))
        .thenReturn(TenantAccess.denied(null));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.tenantCheck().enabled()).isTrue();
    assertThat(result.tenantCheck().tenantIds()).isNull();
  }

  @Test
  void shouldDisableTenantCheckWithWildcardTenantAccess() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(null));
    when(tenantAccessProvider.resolveTenantAccess(eq(authentication)))
        .thenReturn(TenantAccess.wildcard(null));

    final var reference = new AtomicReference<ResourceAccessChecks>();

    // when
    controller.doSearch(
        securityContext,
        a -> {
          reference.set(a);
          return null;
        });

    // then
    final var result = reference.get();
    assertThat(result.tenantCheck().enabled()).isFalse();
    assertThat(result.tenantCheck().tenantIds()).isNull();
  }

  @Test
  void shouldApplyResourceAccessChecksWhileDoGet() {
    // given
    final var resource = new TestResource("invoice", "order", "bar");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization =
        Authorization.of(a -> a.processDefinition().readProcessDefinition().resourceId("invoice"));
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    when(resourceAccessProvider.hasResourceAccess(
            eq(authentication), eq(authorization), any(TestResource.class)))
        .thenReturn(ResourceAccess.allowed(authorization));
    when(tenantAccessProvider.hasTenantAccess(eq(authentication), any()))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    final var result = controller.doGet(securityContext, a -> resource);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldUseResourceIdSupplierWhileDoGet() {
    // given
    final var resource = new TestResource("invoice", "order", "bar");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization =
        Authorization.of(
            (Authorization.Builder<TestResource> a) ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceIdSupplier(TestResource::anotherValue));
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    when(resourceAccessProvider.hasResourceAccess(
            eq(authentication), eq(authorization), any(TestResource.class)))
        .thenReturn(ResourceAccess.allowed(authorization));
    when(tenantAccessProvider.hasTenantAccess(eq(authentication), any()))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    final var result = controller.doGet(securityContext, a -> resource);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldThrowTenantAccessDeniedException() {
    // given
    final var resource = new TestResource("invoice", "order", "bar");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization =
        Authorization.of(a -> a.processDefinition().readProcessDefinition().resourceId("invoice"));
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    when(tenantAccessProvider.hasTenantAccess(eq(authentication), any()))
        .thenReturn(TenantAccess.denied(List.of("bar")));

    // when
    assertThatThrownBy(() -> controller.doGet(securityContext, a -> resource))
        .isInstanceOf(TenantAccessDeniedException.class);
  }

  @Test
  void shouldThrowResourceAccessDeniedExceptionWhenDoingGet() {
    // given
    final var resource = new TestResource("invoice", "order", "bar");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization =
        Authorization.of(a -> a.processDefinition().readProcessDefinition().resourceId("invoice"));
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    when(resourceAccessProvider.hasResourceAccess(
            eq(authentication), eq(authorization), any(TestResource.class)))
        .thenReturn(ResourceAccess.denied(authorization));
    when(tenantAccessProvider.hasTenantAccess(eq(authentication), any()))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    assertThatThrownBy(() -> controller.doGet(securityContext, a -> resource))
        .isInstanceOf(ResourceAccessDeniedException.class);
  }

  @Test
  void shouldNotApplyAnyChecksWhenResourceIsNull() {
    // given
    final var resource = new TestResource("invoice", "order", "bar");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization =
        Authorization.of(a -> a.processDefinition().readProcessDefinition().resourceId("invoice"));
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // when
    final var result = controller.doGet(securityContext, a -> null);

    // then
    assertThat(result).isNull();
    verify(resourceAccessProvider, times(0)).hasResourceAccess(any(), any(), any());
    verify(tenantAccessProvider, times(0)).hasTenantAccess(any(), any());
  }

  record TestResource(String id, String anotherValue, String tenantId)
      implements TenantOwnedEntity {}
}
