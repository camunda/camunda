/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.CloudUserCacheConfiguration;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotAuthorizedException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The class uses a cache to prevent repeated fetching of all members from a Cloud organisation. The minimum interval between
 * repopulating the cache is configurable. If a user does not exist in the cache, a request to fetch that user is made
 * directly to the user client
 */
@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CloudUsersService {

  private final Cache<String, CloudUserDto> cloudUsersCache;

  private static final String ERROR_MISSING_ACCESS_TOKEN = "Missing user access token for service access.";

  private final CCSaaSUserClient userClient;
  private final AccountsUserAccessTokenProvider accessTokenProvider;
  private final ConfigurationService configurationService;

  private OffsetDateTime cacheLastPopulatedTimestamp;

  public CloudUsersService(final CCSaaSUserClient userClient,
                           final AccountsUserAccessTokenProvider accessTokenProvider,
                           final ConfigurationService configurationService) {
    this.userClient = userClient;
    this.accessTokenProvider = accessTokenProvider;
    this.configurationService = configurationService;

    final CloudUserCacheConfiguration cloudUsersCacheConfiguration = configurationService.getCaches().getCloudUsers();
    cloudUsersCache = Caffeine.newBuilder()
      .maximumSize(cloudUsersCacheConfiguration.getMaxSize())
      .build();
    cacheLastPopulatedTimestamp = OffsetDateTime.MIN;
  }

  public Optional<CloudUserDto> getUserById(final String userId) {
    final Optional<CloudUserDto> cloudUser = Optional.ofNullable(cloudUsersCache.getIfPresent(userId));
    if (cloudUser.isPresent()) {
      return cloudUser;
    }
    final Optional<CloudUserDto> fetchedUser = accessTokenProvider.getCurrentUsersAccessToken()
      .map(accessToken -> userClient.getCloudUserById(userId, accessToken))
      .orElseThrow(() -> new NotAuthorizedException(ERROR_MISSING_ACCESS_TOKEN));
    fetchedUser.ifPresent(user -> cloudUsersCache.put(user.getUserId(), user));
    return fetchedUser;
  }

  /**
   * Returns the users currently in the user cache
   */
  public Collection<CloudUserDto> getAllUsers() {
    repopulateCacheIfMinFetchIntervalExceeded();
    return cloudUsersCache.asMap().values();
  }

  private synchronized void repopulateCacheIfMinFetchIntervalExceeded() {
    final OffsetDateTime currentTime = LocalDateUtil.getCurrentDateTime();
    final long secondsSinceCacheRepopulated = cacheLastPopulatedTimestamp.until(currentTime, ChronoUnit.SECONDS);
    if (secondsSinceCacheRepopulated > configurationService.getCaches().getCloudUsers().getMinFetchIntervalSeconds()) {
      cloudUsersCache.putAll(fetchAllUsersWithinOrganization());
      cacheLastPopulatedTimestamp = currentTime;
    }
  }

  /**
   * This fetches the users of the organization from the Cloud Users client.
   */
  private Map<String, CloudUserDto> fetchAllUsersWithinOrganization() {
    return accessTokenProvider.getCurrentUsersAccessToken()
      .map(userClient::fetchAllCloudUsers)
      .orElseThrow(() -> new NotAuthorizedException(ERROR_MISSING_ACCESS_TOKEN))
      .stream()
      // We remove the users without a user ID
      .filter(user -> user.getUserId() != null)
      .collect(Collectors.toMap(CloudUserDto::getUserId, Function.identity()));
  }
}
