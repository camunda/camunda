/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static java.util.stream.Collectors.toMap;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.users.dto.User;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.service.security.CCSMTokenService;
import io.camunda.optimize.service.util.configuration.CacheConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSMCondition.class)
public class CCSMUserCache {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(CCSMUserCache.class);
  private final Cache<String, UserDto> ccsmUsersCache;
  private final CCSMTokenService ccsmTokenService;
  private final Identity identity;

  public CCSMUserCache(
      final ConfigurationService configurationService,
      final CCSMTokenService ccsmTokenService,
      final Identity identity) {
    this.ccsmTokenService = ccsmTokenService;
    this.identity = identity;
    final CacheConfiguration userCacheConfig = configurationService.getCaches().getUsers();
    ccsmUsersCache =
        Caffeine.newBuilder()
            .maximumSize(userCacheConfig.getMaxSize())
            .expireAfterWrite(Duration.ofMillis(userCacheConfig.getDefaultTtlMillis()))
            .build();
  }

  public Optional<UserDto> getUserById(final String userId) {
    return Optional.ofNullable(
        ccsmUsersCache.get(userId, id -> fetchUserFromIdentity(id).orElse(null)));
  }

  public List<UserDto> getUsersById(final Set<String> userIds) {
    return getUsersById(userIds, userIds.size());
  }

  public List<UserDto> getUsersById(final Set<String> userIds, final int resultLimit) {
    final Map<String, UserDto> cachedUsers = ccsmUsersCache.getAllPresent(userIds);
    final List<UserDto> result =
        cachedUsers.values().stream().limit(resultLimit).collect(Collectors.toList());
    userIds.removeAll(cachedUsers.keySet());
    if (!userIds.isEmpty() && result.size() < resultLimit) {
      final Map<String, UserDto> usersInIdentity = fetchUsersByIdFromIdentityIfExists(userIds);
      result.addAll(usersInIdentity.values());
      ccsmUsersCache.putAll(usersInIdentity);
    }
    return result.stream().limit(resultLimit).toList();
  }

  public List<UserDto> searchForIdentityUsingSearchTerm(
      final String searchString, final int maxResults) {
    List<UserDto> result =
        StringUtils.isBlank(searchString)
            ? ccsmUsersCache.asMap().values().stream().limit(maxResults).toList()
            : ccsmUsersCache.asMap().values().stream()
                .filter(user -> user.isIdentityContainsSearchTerm(searchString))
                .limit(maxResults)
                .toList();
    if (result.isEmpty()) {
      result = searchUsersInIdentity(searchString, maxResults);
    }
    return result;
  }

  public List<UserDto> searchForUsersUsingEmails(final Set<String> emails) {
    final List<UserDto> result =
        ccsmUsersCache.asMap().values().stream()
            .filter(user -> StringUtils.equalsAny(user.getEmail(), emails.toArray(new String[0])))
            .collect(Collectors.toList());
    if (result.size() < emails.size()) {
      emails.removeAll(result.stream().map(UserDto::getEmail).collect(Collectors.toSet()));
      result.addAll(
          emails.stream()
              .flatMap(email -> searchUsersInIdentity(email, 1).stream())
              .filter(user -> StringUtils.equalsAny(user.getEmail(), emails.toArray(new String[0])))
              .toList());
    }
    return result;
  }

  private Optional<UserDto> getUserFromIdentity(final String token, final String userId) {
    try {
      return identity.users().withAccessToken(token).get(Collections.singletonList(userId)).stream()
          .findFirst()
          .map(this::mapToUserDto);
    } catch (final Exception e) {
      log.warn("Failed retrieving user by ID from Camunda Identity.", e);
      return Optional.empty();
    }
  }

  private Optional<UserDto> fetchUserFromIdentity(final String userId) {
    if (identity.users().isAvailable()) {
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      if (token.isPresent()) {
        return getUserFromIdentity(token.get(), userId);
      } else {
        log.warn("Could not retrieve user because no user token present.");
        return Optional.empty();
      }
    } else {
      log.info(
          "Cannot search for user by ID because user search not available in Camunda identity.");
      return Optional.empty();
    }
  }

  private Map<String, UserDto> fetchUsersByIdFromIdentityIfExists(final Set<String> userIds) {
    if (identity.users().isAvailable()) {
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      if (token.isPresent()) {
        // We have to iterate over the list of userIds one by one because the list get endpoint in
        // identity returns a 404 if even one of the given userIds does not exist
        return userIds.stream()
            .map(id -> getUserFromIdentity(token.get(), id))
            .flatMap(Optional::stream)
            .collect(toMap(UserDto::getId, Function.identity()));
      } else {
        log.warn("Could not retrieve user because no user token present.");
        return Collections.emptyMap();
      }
    } else {
      log.info(
          "Cannot search for user by ID because user search not available in Camunda identity.");
      return Collections.emptyMap();
    }
  }

  private List<UserDto> searchUsersInIdentity(String searchString, final int maxResults) {
    if (identity.users().isAvailable()) {
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      searchString = searchString.toLowerCase(Locale.ENGLISH);
      if (token.isPresent()) {
        try {
          return identity.users().withAccessToken(token.get()).search(searchString).stream()
              .limit(maxResults)
              .map(this::mapToUserDto)
              .toList();
        } catch (final Exception e) {
          log.warn(
              "Failed searching for users with searchString [{}] in Camunda Identity.",
              searchString,
              e);
          return Collections.emptyList();
        }
      } else {
        log.warn("Could not search for users because no user token present.");
        return Collections.emptyList();
      }
    } else {
      log.info("Cannot search for users because no user search available in Camunda Identity.");
      return Collections.emptyList();
    }
  }

  @NotNull
  private UserDto mapToUserDto(final User user) {
    return new UserDto(user.getId(), user.getName(), user.getEmail(), Collections.emptyList());
  }
}
