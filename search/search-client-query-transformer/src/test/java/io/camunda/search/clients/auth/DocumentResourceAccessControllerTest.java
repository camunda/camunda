/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.exception.ResourceAccessDeniedException.MissingAuthorization;
import io.camunda.search.exception.TenantAccessDeniedException;
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
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
  void shouldEnableAuthorizationCheckOnSearch() {
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

    assertThat(result.authorizationCheck().authorizationCondition())
        .isEqualTo(AuthorizationConditions.single(requiredAuthorization));
  }

  @Test
  void shouldEnableAuthorizationCheckOnSearchWithAnyOfAuthorizationCondition() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var processDefinitionReadAuth =
        Authorization.of(a -> a.processDefinition().readUserTask());
    final var userTaskReadAuth = Authorization.of(a -> a.userTask().read());
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationCondition(
                        AuthorizationConditions.anyOf(
                            processDefinitionReadAuth, userTaskReadAuth)));

    final var resolvedProcessDefinitionAuth =
        Authorization.of(
            a -> a.processDefinition().readUserTask().resourceIds(List.of("pd-1", "pd-2")));
    final var resolvedUserTaskAuth =
        Authorization.of(a -> a.userTask().read().resourceIds(List.of("ut-1", "ut-2")));

    when(resourceAccessProvider.resolveResourceAccess(authentication, processDefinitionReadAuth))
        .thenReturn(ResourceAccess.allowed(resolvedProcessDefinitionAuth));
    when(resourceAccessProvider.resolveResourceAccess(authentication, userTaskReadAuth))
        .thenReturn(ResourceAccess.allowed(resolvedUserTaskAuth));
    when(tenantAccessProvider.resolveTenantAccess(authentication))
        .thenReturn(TenantAccess.wildcard(null));

    // when - then
    controller.doSearch(
        securityContext,
        checks -> {
          assertThat(checks.authorizationCheck().enabled()).isTrue();
          assertThat(checks.authorizationCheck().authorizationCondition())
              .isEqualTo(
                  AuthorizationConditions.anyOf(
                      resolvedProcessDefinitionAuth, resolvedUserTaskAuth));
          return null;
        });
  }

  @Test
  void shouldDisableAuthorizationCheckWithWildcardResourceAccessOnSearch() {
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
    assertThat(result.authorizationCheck().authorizationCondition()).isNull();
  }

  @Test
  void shouldDisableAuthorizationCheckOnSearchWithAnyOfAuthorizationConditionWhenWildcardAccess() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var processDefinitionReadAuth =
        Authorization.of(a -> a.processDefinition().readUserTask());
    final var userTaskReadAuth = Authorization.of(a -> a.userTask().read());
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationCondition(
                        AuthorizationConditions.anyOf(
                            processDefinitionReadAuth, userTaskReadAuth)));

    final var wildcardAuthorization =
        Authorization.of(a -> a.processDefinition().readUserTask().resourceIds(List.of("*")));

    when(resourceAccessProvider.resolveResourceAccess(authentication, processDefinitionReadAuth))
        .thenReturn(ResourceAccess.wildcard(wildcardAuthorization));
    when(tenantAccessProvider.resolveTenantAccess(authentication))
        .thenReturn(TenantAccess.wildcard(null));

    // when - then
    controller.doSearch(
        securityContext,
        checks -> {
          assertThat(checks.authorizationCheck().enabled()).isFalse();
          assertThat(checks.authorizationCheck().authorizationCondition()).isNull();
          return null;
        });

    // verify that second authorization was not resolved,
    // as wildcard access was already granted by the first
    verify(resourceAccessProvider, never()).resolveResourceAccess(authentication, userTaskReadAuth);
  }

  @Test
  void shouldEnableResourceAccessCheckOnSearchEvenWhenNoResourceIdsProvided() {
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
    assertThat(result.authorizationCheck().authorizationCondition())
        .isEqualTo(AuthorizationConditions.single(requiredAuthorization));
  }

  @Test
  void shouldEnableTenantCheckOnSearch() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo").tenants(List.of("bar")));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(mock(Authorization.class)));
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
  void shouldEnableTenantCheckOnSearchEvenWhenTenantAccessDenied() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(mock(Authorization.class)));
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
  void shouldDisableTenantCheckWithWildcardTenantAccessOnSearch() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));

    // required authorization
    when(resourceAccessProvider.resolveResourceAccess(any(), any()))
        .thenReturn(ResourceAccess.wildcard(mock(Authorization.class)));
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
  void shouldReturnResourceOnGetWhenAccessAllowedBySingleAuthorization() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));
    final var document = "document";

    when(resourceAccessProvider.hasResourceAccess(authentication, authorization, document))
        .thenReturn(ResourceAccess.allowed(authorization));
    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    final var result = controller.doGet(securityContext, doGetAccessCheckApplier(document));

    // then
    assertThat(result).isEqualTo(document);
  }

  @Test
  void shouldReturnResourceOnGetWhenAccessAllowedByFirstAnyOfAuthorization() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var processDefinitionReadAuth =
        Authorization.of(a -> a.processDefinition().readUserTask());
    final var userTaskReadAuth = Authorization.of(a -> a.userTask().read());
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationCondition(
                        AuthorizationConditions.anyOf(
                            processDefinitionReadAuth, userTaskReadAuth)));
    final var document = "document";

    when(resourceAccessProvider.hasResourceAccess(
            authentication, processDefinitionReadAuth, document))
        .thenReturn(ResourceAccess.allowed(processDefinitionReadAuth));
    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    final var result = controller.doGet(securityContext, doGetAccessCheckApplier(document));

    // then
    assertThat(result).isEqualTo(document);
    // verify that the check for the second authorization was not performed,
    // as access was already granted by the first
    verify(resourceAccessProvider, never())
        .hasResourceAccess(authentication, userTaskReadAuth, document);
  }

  @Test
  void shouldReturnResourceOnGetWhenAccessAllowedByLastAnyOfAuthorization() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var processDefinitionReadAuth =
        Authorization.of(a -> a.processDefinition().readUserTask());
    final var userTaskReadAuth = Authorization.of(a -> a.userTask().read());
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationCondition(
                        AuthorizationConditions.anyOf(
                            processDefinitionReadAuth, userTaskReadAuth)));
    final var document = "document";

    when(resourceAccessProvider.hasResourceAccess(
            authentication, processDefinitionReadAuth, document))
        .thenReturn(ResourceAccess.denied(processDefinitionReadAuth));
    when(resourceAccessProvider.hasResourceAccess(authentication, userTaskReadAuth, document))
        .thenReturn(ResourceAccess.allowed(userTaskReadAuth));
    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when
    final var result = controller.doGet(securityContext, doGetAccessCheckApplier(document));

    // then
    assertThat(result).isEqualTo(document);
  }

  @Test
  void shouldThrowResourceAccessDeniedOnGetWhenAccessDeniedForSingleAuthorization() {
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));
    final var document = "document";

    when(resourceAccessProvider.hasResourceAccess(authentication, authorization, document))
        .thenReturn(ResourceAccess.denied(authorization));
    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when - then
    assertThatExceptionOfType(ResourceAccessDeniedException.class)
        .isThrownBy(() -> controller.doGet(securityContext, doGetAccessCheckApplier(document)))
        .withMessage(
            "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION'")
        .satisfies(
            err -> {
              assertThat(err.getReason()).isEqualTo(Reason.FORBIDDEN);
              assertThat(err.getMissingAuthorizations())
                  .containsExactly(
                      new MissingAuthorization(
                          AuthorizationResourceType.PROCESS_DEFINITION,
                          PermissionType.READ_PROCESS_DEFINITION));
            });
  }

  @Test
  void shouldThrowResourceAccessDeniedOnGetWhenAccessDeniedForAnyOfAuthorizations() {
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var processDefinitionReadAuth =
        Authorization.of(a -> a.processDefinition().readUserTask());
    final var userTaskReadAuth = Authorization.of(a -> a.userTask().read());
    final var securityContext =
        SecurityContext.of(
            s ->
                s.withAuthentication(authentication)
                    .withAuthorizationCondition(
                        AuthorizationConditions.anyOf(
                            processDefinitionReadAuth, userTaskReadAuth)));
    final var document = "document";

    when(resourceAccessProvider.hasResourceAccess(
            authentication, processDefinitionReadAuth, document))
        .thenReturn(ResourceAccess.denied(processDefinitionReadAuth));
    when(resourceAccessProvider.hasResourceAccess(authentication, userTaskReadAuth, document))
        .thenReturn(ResourceAccess.denied(userTaskReadAuth));
    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.allowed(List.of("bar")));

    // when - then
    assertThatExceptionOfType(ResourceAccessDeniedException.class)
        .isThrownBy(() -> controller.doGet(securityContext, doGetAccessCheckApplier(document)))
        .withMessage(
            "Unauthorized to perform any of the operations: 'READ_USER_TASK' on 'PROCESS_DEFINITION' or 'READ' on 'USER_TASK'")
        .satisfies(
            err -> {
              assertThat(err.getReason()).isEqualTo(Reason.FORBIDDEN);
              assertThat(err.getMissingAuthorizations())
                  .containsExactly(
                      new MissingAuthorization(
                          AuthorizationResourceType.PROCESS_DEFINITION,
                          PermissionType.READ_USER_TASK),
                      new MissingAuthorization(
                          AuthorizationResourceType.USER_TASK, PermissionType.READ));
            });
  }

  @Test
  void shouldThrowTenantAccessDeniedOnGetWhenNoTenantAccess() {
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());
    final var securityContext =
        SecurityContext.of(
            s -> s.withAuthentication(authentication).withAuthorization(authorization));
    final var document = "document";

    when(tenantAccessProvider.hasTenantAccess(authentication, document))
        .thenReturn(TenantAccess.denied(List.of("deniedTenant")));

    // when - then
    assertThatExceptionOfType(TenantAccessDeniedException.class)
        .isThrownBy(() -> controller.doGet(securityContext, doGetAccessCheckApplier(document)))
        .withMessage("Tenant access was denied")
        .satisfies(err -> assertThat(err.getReason()).isEqualTo(Reason.FORBIDDEN));
  }

  private static <T> Function<ResourceAccessChecks, T> doGetAccessCheckApplier(final T document) {
    return checks -> {
      assertThat(checks).isEqualTo(ResourceAccessChecks.disabled());
      return document;
    };
  }
}
