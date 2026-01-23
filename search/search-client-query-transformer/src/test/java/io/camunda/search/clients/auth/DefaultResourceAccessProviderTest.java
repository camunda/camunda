/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.impl.AuthorizationChecker;
import io.camunda.zeebe.protocol.record.value.AuthorizationScope;
import java.util.List;
import java.util.Set;
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
    final var authScopeBar = AuthorizationScope.of("bar");
    final var authScopeBaz = AuthorizationScope.of("baz");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of(authScopeBar, authScopeBaz));

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

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of(AuthorizationScope.WILDCARD));

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

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of());

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }

  @Test
  void shouldResolveResourceAccessForAllPropertyNames() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourcePropertyNames(Set.of("propA", "propB", "propC")));

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(
            List.of(
                AuthorizationScope.property("propA"),
                AuthorizationScope.property("propB"),
                AuthorizationScope.property("propC")));

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourceType()).isEqualTo(authorization.resourceType());
    assertThat(result.authorization().permissionType()).isEqualTo(authorization.permissionType());
    assertThat(result.authorization().resourcePropertyNames())
        .containsExactlyInAnyOrder("propA", "propB", "propC");
  }

  @Test
  void shouldResolvePartialResourceAccessByPropertyNames() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourcePropertyNames(Set.of("propA", "propB", "propC")));

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of(AuthorizationScope.property("propB")));

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames()).containsExactly("propB");
  }

  @Test
  void shouldDenyResourceAccessByPropertyNamesWhenNoneRetrieved() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a -> a.processDefinition().readProcessDefinition().authorizedByProperty("propB"));

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of());

    // when
    final var result = resourceAccessProvider.resolveResourceAccess(authentication, authorization);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames()).isEmpty();
  }

  @Test
  void shouldAllowAccessToResourceByIdBasedAuthorization() {
    // given
    final var authScopeInvoice = AuthorizationScope.id("invoice");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceId(authScopeInvoice.getResourceId()));

    when(authorizationChecker.isAuthorized(
            eq(authScopeInvoice), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(true);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(
            authentication, authorization, new TestResource("invoice", "order"));

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }

  @Test
  void shouldDenyAccessToResourceByIdBasedAuthorization() {
    // given
    final var authScopeInvoice = AuthorizationScope.id("invoice");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceId(authScopeInvoice.getResourceId()));

    when(authorizationChecker.isAuthorized(
            eq(authScopeInvoice), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(false);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(
            authentication,
            authorization,
            new TestResource(authScopeInvoice.getResourceId(), "order"));

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }

  @Test
  void shouldAllowAccessToResourceByResourceIdSupplier() {
    // given
    final var authScopeOrder = AuthorizationScope.id("order");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            (Authorization.Builder<TestResource> a) ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceIdSupplier(TestResource::anotherValue));

    when(authorizationChecker.isAuthorized(
            eq(authScopeOrder), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(true);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(
            authentication, authorization, new TestResource("invoice", "order"));

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourceIds()).containsExactlyInAnyOrder("order");
  }

  @Test
  void shouldDenyAccessToResourceByResourceIdSupplier() {
    // given
    final var authScopeOrder = AuthorizationScope.id("order");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            (Authorization.Builder<TestResource> a) ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceIdSupplier(TestResource::anotherValue));

    when(authorizationChecker.isAuthorized(
            eq(authScopeOrder), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(false);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(
            authentication, authorization, new TestResource("invoice", "order"));

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourceIds()).containsExactlyInAnyOrder("order");
  }

  @Test
  void shouldThrowExceptionIfMultipleResourceIdsAreProvided() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceIds(List.of("invoice", "order")));

    // when
    assertThatThrownBy(
            () ->
                resourceAccessProvider.hasResourceAccess(
                    authentication, authorization, new TestResource("invoice", "order")))
        .isInstanceOf(CamundaSearchException.class);
  }

  @Test
  void shouldThrowExceptionIfNoResourceIdsProvided() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization = Authorization.of(a -> a.processDefinition().readProcessDefinition());

    // when
    assertThatThrownBy(
            () ->
                resourceAccessProvider.hasResourceAccess(
                    authentication, authorization, new TestResource("invoice", "order")))
        .isInstanceOf(CamundaSearchException.class);
  }

  @Test
  void shouldAllowAccessToResourceByPropertyBasedAuthorization() {
    // given
    final var authentication =
        CamundaAuthentication.of(a -> a.user("trevor").groupIds(List.of("managers")));
    final var authorization =
        Authorization.of(
            a ->
                a.userTask()
                    .readUserTask()
                    .authorizedByAssignee()
                    .or()
                    .authorizedByCandidateGroups());

    final var userTask = createUserTask("trevor");

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(
            List.of(
                AuthorizationScope.property(Authorization.PROP_ASSIGNEE),
                AuthorizationScope.property(Authorization.PROP_CANDIDATE_USERS),
                AuthorizationScope.property(Authorization.PROP_CANDIDATE_GROUPS)));

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(authentication, authorization, userTask);

    // then
    assertThat(result.allowed()).isTrue();
    assertThat(result.denied()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames())
        .containsExactlyInAnyOrder(
            Authorization.PROP_ASSIGNEE, Authorization.PROP_CANDIDATE_GROUPS);
  }

  @Test
  void shouldDenyAccessToResourceByPropertyBasedAuthorizationWhenNoPropertiesAuthorized() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("jimmy").groupIds(List.of()));
    final var authorization =
        Authorization.of(a -> a.userTask().readUserTask().authorizedByAssignee());

    final var userTask = createUserTask("jimmy");

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of());

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(authentication, authorization, userTask);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames()).isEmpty();
  }

  @Test
  void shouldDenyAccessToResourceByPropertyBasedAuthorizationWhenResourceDoesNotMatch() {
    // given
    final var authentication =
        CamundaAuthentication.of(a -> a.user("franklin").groupIds(List.of()));
    final var authorization =
        Authorization.of(a -> a.userTask().readUserTask().authorizedByAssignee());

    // User task is assigned to someone else
    final var userTask = createUserTask("michael");

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of(AuthorizationScope.property(Authorization.PROP_ASSIGNEE)));

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(authentication, authorization, userTask);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames())
        .containsExactly(Authorization.PROP_ASSIGNEE);
  }

  @Test
  void shouldDenyAccessToResourceByPropertyBasedAuthorizationWhenNoMatcherRegisteredForResource() {
    // given
    final var authentication = CamundaAuthentication.of(a -> a.user("martin"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .authorizedByProperty("anotherValue")
                    .or()
                    .authorizedByProperty("unknownProperty"));

    final var resource = new TestResource("id123", "value456");

    when(authorizationChecker.retrieveAuthorizedAuthorizationScopes(
            any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(List.of(AuthorizationScope.property("anotherValue")));

    // when
    final var result =
        resourceAccessProvider.hasResourceAccess(authentication, authorization, resource);

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization().resourcePropertyNames()).containsExactly("anotherValue");
  }

  @Test
  void shouldAllowAccessToResourceByResourceId() {
    // given
    final var authScopeInvoice = AuthorizationScope.id("invoice");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceId(authScopeInvoice.getResourceId()));

    when(authorizationChecker.isAuthorized(
            eq(authScopeInvoice), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(true);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccessByResourceId(
            authentication, authorization, "invoice");

    // then
    assertThat(result.denied()).isFalse();
    assertThat(result.allowed()).isTrue();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }

  @Test
  void shouldDenyAccessToResourceByResourceId() {
    // given
    final var authScopeInvoice = AuthorizationScope.id("invoice");
    final var authentication = CamundaAuthentication.of(a -> a.user("foo"));
    final var authorization =
        Authorization.of(
            a ->
                a.processDefinition()
                    .readProcessDefinition()
                    .resourceId(authScopeInvoice.getResourceId()));

    when(authorizationChecker.isAuthorized(
            eq(authScopeInvoice), any(CamundaAuthentication.class), any(Authorization.class)))
        .thenReturn(false);

    // when
    final var result =
        resourceAccessProvider.hasResourceAccessByResourceId(
            authentication, authorization, "invoice");

    // then
    assertThat(result.denied()).isTrue();
    assertThat(result.allowed()).isFalse();
    assertThat(result.wildcard()).isFalse();
    assertThat(result.authorization()).isEqualTo(authorization);
  }

  private UserTaskEntity createUserTask(final String assignee) {
    final var mockedTask = mock(UserTaskEntity.class);
    lenient().when(mockedTask.assignee()).thenReturn(assignee);
    return mockedTask;
  }

  record TestResource(String id, String anotherValue) {}
}
