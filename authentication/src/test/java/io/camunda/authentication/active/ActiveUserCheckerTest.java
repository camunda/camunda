/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.UserEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.UserServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActiveUserCheckerTest {

  private static final String TENANT = "default";

  private final UserServices userServices = mock(UserServices.class);
  private final GroupServices groupServices = mock(GroupServices.class);
  private final ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
  private final AtomicLong clock = new AtomicLong();
  private ActiveUserChecker checker;

  @BeforeEach
  void setUp() {
    when(serviceRegistry.userServices(TENANT)).thenReturn(userServices);
    when(serviceRegistry.groupServices(TENANT)).thenReturn(groupServices);
    checker = new ActiveUserChecker(() -> serviceRegistry, Duration.ofSeconds(30), clock::get);
  }

  private static UserEntity user(final String username, final Boolean active) {
    return new UserEntity(1L, username, "name", "email", "pw", "", active);
  }

  @Test
  void shouldCacheAttributesOfDirectGroupsSkippingGroupsWithoutAttributes() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", true));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenReturn(
            List.of(
                new GroupEntity(1L, "g1", "n", null, null, Map.of("region", "EMEA"), null, null),
                new GroupEntity(2L, "g2", "n", null, null, Map.of(), null, null),
                new GroupEntity(3L, "g3", "n", null, null, null, null, null)));

    // when
    final var first = checker.user(TENANT, "jane");
    final var second = checker.user(TENANT, "jane");

    // then
    assertThat(first.groupAttributes()).containsExactly(Map.of("region", "EMEA"));
    assertThat(second).isSameAs(first);
    verify(groupServices, times(1)).getGroupsByMemberTypeAndMemberIds(any(), any());
  }

  @Test
  void shouldYieldNoGroupAttributesWhenGroupLookupFails() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", false));
    when(groupServices.getGroupsByMemberTypeAndMemberIds(any(), any()))
        .thenThrow(new ServiceException("down", Status.UNAVAILABLE));

    // when
    final var result = checker.user(TENANT, "jane");

    // then
    assertThat(result.groupAttributes()).isEmpty();
    assertThat(result.active()).isFalse();
  }

  @Test
  void shouldReportInactiveWhenStoredUserIsDeactivated() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", false));

    // when / then
    assertThat(checker.isActive(TENANT, "jane")).isFalse();
  }

  @Test
  void shouldReportActiveWhenStoredUserIsActive() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", true));

    // when / then
    assertThat(checker.isActive(TENANT, "jane")).isTrue();
  }

  @Test
  void shouldReportActiveWhenActiveFlagWasNeverWritten() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", null));

    // when / then
    assertThat(checker.isActive(TENANT, "jane")).isTrue();
  }

  @Test
  void shouldReportActiveWhenUserIsNotStored() {
    // given
    when(userServices.getUser(eq("jane"), any()))
        .thenThrow(new ServiceException("not found", Status.NOT_FOUND));

    // when / then
    assertThat(checker.isActive(TENANT, "jane")).isTrue();
  }

  @Test
  void shouldFailOpenWhenLookupFails() {
    // given
    when(userServices.getUser(eq("jane"), any()))
        .thenThrow(new ServiceException("unavailable", Status.UNAVAILABLE));

    // when / then
    assertThat(checker.isActive(TENANT, "jane")).isTrue();
  }

  @Test
  void shouldNotLookUpAgainWithinTtl() {
    // given
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", false));
    checker.isActive(TENANT, "jane");

    // when
    clock.addAndGet(Duration.ofSeconds(29).toNanos());
    final var active = checker.isActive(TENANT, "jane");

    // then
    assertThat(active).isFalse();
    verify(userServices, times(1)).getUser(eq("jane"), any());
  }

  @Test
  void shouldLookUpAgainAfterTtl() {
    // given
    when(userServices.getUser(eq("jane"), any()))
        .thenReturn(user("jane", true), user("jane", false));
    assertThat(checker.isActive(TENANT, "jane")).isTrue();

    // when
    clock.addAndGet(Duration.ofSeconds(31).toNanos());

    // then
    assertThat(checker.isActive(TENANT, "jane")).isFalse();
    verify(userServices, times(2)).getUser(eq("jane"), any());
  }

  @Test
  void shouldNotCacheWhenTtlIsZero() {
    // given
    checker = new ActiveUserChecker(() -> serviceRegistry, Duration.ZERO, clock::get);
    when(userServices.getUser(eq("jane"), any())).thenReturn(user("jane", true));

    // when
    checker.isActive(TENANT, "jane");
    checker.isActive(TENANT, "jane");

    // then
    verify(userServices, times(2)).getUser(eq("jane"), any());
  }

  @Test
  void shouldReturnStoredAttributesAndExternalId() {
    // given
    when(userServices.getUser(eq("jane"), any()))
        .thenReturn(
            new UserEntity(
                1L, "jane", "name", "email", "pw", "ext-1", true, Map.of("department", "Finance")));

    // when
    final var user = checker.user(TENANT, "jane");

    // then
    assertThat(user.active()).isTrue();
    assertThat(user.externalId()).isEqualTo("ext-1");
    assertThat(user.attributes()).containsEntry("department", "Finance");
  }

  @Test
  void shouldReturnNoAttributesWhenUserIsNotStoredOrLookupFails() {
    // given
    when(userServices.getUser(eq("missing"), any()))
        .thenThrow(new ServiceException("not found", Status.NOT_FOUND));
    when(userServices.getUser(eq("broken"), any()))
        .thenThrow(new ServiceException("unavailable", Status.UNAVAILABLE));

    // when / then
    assertThat(checker.user(TENANT, "missing").attributes()).isEmpty();
    assertThat(checker.user(TENANT, "missing").active()).isTrue();
    assertThat(checker.user(TENANT, "broken").attributes()).isEmpty();
    assertThat(checker.user(TENANT, "broken").active()).isTrue();
  }

  @Test
  void shouldServeAttributesFromTheSameCacheEntryAsTheActiveFlag() {
    // given
    when(userServices.getUser(eq("jane"), any()))
        .thenReturn(
            new UserEntity(
                1L, "jane", "name", "email", "pw", "", true, Map.of("department", "Finance")));

    // when
    checker.isActive(TENANT, "jane");
    final var user = checker.user(TENANT, "jane");

    // then
    assertThat(user.attributes()).containsEntry("department", "Finance");
    verify(userServices, times(1)).getUser(eq("jane"), any());
  }
}
