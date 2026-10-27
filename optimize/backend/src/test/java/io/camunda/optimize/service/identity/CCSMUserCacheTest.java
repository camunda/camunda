/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.users.Users;
import io.camunda.identity.sdk.users.dto.User;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CCSMUserCacheTest {

  public static final String ACCESS_TOKEN = "testToken";
  public static final UserDto TEST_USER_1 =
      new UserDto("123", "Donna Noble", "donna@email.com", Collections.emptyList());
  public static final UserDto TEST_USER_2 =
      new UserDto("456", "John Smith", "john@email.com", Collections.emptyList());

  @Mock Identity identity;
  @Mock Users users;
  @Mock CCSMTokenService ccsmTokenService;
  @Mock Cache<String, UserDto> ccsmUsersCache;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

  private CCSMUserCache underTest;

  @BeforeEach
  public void setup() {
    when(configurationService.getCaches().getUsers().getMaxSize()).thenReturn(10000);
    when(configurationService.getCaches().getUsers().getDefaultTtlMillis()).thenReturn(600000);
    when(ccsmTokenService.getCurrentUserAuthToken()).thenReturn(Optional.of(ACCESS_TOKEN));
    when(identity.users()).thenReturn(users);
    when(users.withAccessToken(ACCESS_TOKEN)).thenReturn(users);
    when(users.isAvailable()).thenReturn(true);
    underTest = new CCSMUserCache(configurationService, ccsmTokenService, identity);
    final Field ccsmUsersCacheField =
        ReflectionUtils.findFields(
                CCSMUserCache.class,
                f -> "ccsmUsersCache".equals(f.getName()),
                HierarchyTraversalMode.TOP_DOWN)
            .get(0);
    ccsmUsersCacheField.setAccessible(true);
    try {
      ccsmUsersCacheField.set(underTest, ccsmUsersCache);
    } catch (final IllegalAccessException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  @Test
  public void searchesUserCacheWithSearchStringOneMatch() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult =
        underTest.searchForIdentityUsingSearchTerm(TEST_USER_1.getFirstName(), 10);

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1);
    verify(identity, never()).users();
  }

  @Test
  public void searchesUserCacheWithSearchStringMultipleMatches() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult = underTest.searchForIdentityUsingSearchTerm("email", 10);

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrder(TEST_USER_1, TEST_USER_2);
    verify(identity, never()).users();
  }

  @Test
  public void returnsAllUsersInCacheOnSearchWithEmptySearchTerm() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult = underTest.searchForIdentityUsingSearchTerm("", 10);

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrder(TEST_USER_1, TEST_USER_2);
  }

  @Test
  public void searchForUsersInCacheUsingEmailOneEmail() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult =
        underTest.searchForUsersUsingEmails(Set.of("donna@email.com"));

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1);
    verify(identity, never()).users();
  }

  @Test
  public void searchForUsersInCacheUsingEmailMultipleEmailsMultipleMatches() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult =
        underTest.searchForUsersUsingEmails(
            Stream.of(TEST_USER_1.getEmail(), TEST_USER_2.getEmail()).collect(Collectors.toSet()));

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactlyInAnyOrder(TEST_USER_1, TEST_USER_2);
    verify(identity, never()).users();
  }

  @Test
  public void searchForUsersInCacheUsingEmailMultipleEmailsOneMatch() {
    // given
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));
    when(users.search(any())).thenReturn(Collections.emptyList());

    // when
    final List<UserDto> searchResult =
        underTest.searchForUsersUsingEmails(
            Stream.of(TEST_USER_2.getEmail(), "test@email.com").collect(Collectors.toSet()));

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_2);
    // users() was called twice: once to check availability, once to search
    verify(identity, times(2)).users();
  }

  @Test
  public void searchForUsersInCacheUsingEmailNoPartialMatching() {
    // given botch cache and identity contain users whose email matches the searchstring partially
    when(ccsmUsersCache.asMap())
        .thenReturn(
            new ConcurrentHashMap<>(
                Map.of(TEST_USER_1.getId(), TEST_USER_1, TEST_USER_2.getId(), TEST_USER_2)));
    when(users.search(any()))
        .thenReturn(
            List.of(
                new User(
                    TEST_USER_1.getId(),
                    "donnasUserName",
                    TEST_USER_1.getName(),
                    TEST_USER_1.getEmail())));

    // when
    final List<UserDto> searchResult =
        underTest.searchForUsersUsingEmails(Stream.of("email.com").collect(Collectors.toSet()));

    // then
    assertThat(searchResult).isEmpty();
  }

  @Test
  public void identityIsSearchedIfNoSearchResultInCache() {
    // given
    when(users.search(any()))
        .thenReturn(
            List.of(
                new User(
                    TEST_USER_1.getId(),
                    "donnasUserName",
                    TEST_USER_1.getName(),
                    TEST_USER_1.getEmail())));
    when(ccsmUsersCache.asMap())
        .thenReturn(new ConcurrentHashMap<>(Map.of(TEST_USER_2.getId(), TEST_USER_2)));

    // when
    final List<UserDto> searchResult =
        underTest.searchForIdentityUsingSearchTerm(TEST_USER_1.getFirstName(), 10);

    // then
    assertThat(searchResult)
        .usingRecursiveFieldByFieldElementComparator()
        .containsExactly(TEST_USER_1);
    // users() was called twice: once to check availability, once to search
    verify(identity, times(2)).users();
  }
}
