/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.rest.cloud.CCSaaSUserCache;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSIdentityService extends AbstractIdentityService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CCSaaSIdentityService.class);
  private final CCSaaSUserCache usersCache;

  public CCSaaSIdentityService(
      final ConfigurationService configurationService, final CCSaaSUserCache usersCache) {
    super(configurationService);
    this.usersCache = usersCache;
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    return usersCache.getUserById(userId).map(this::mapToUserDto);
  }

  @Override
  public Optional<UserDto> getCurrentUserById(
      final String userId, final HttpServletRequest request) {
    return getUserById(userId);
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
    // Groups do not exist in SaaS
    return Optional.empty();
  }

  @Override
  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return Collections.emptyList();
  }

  @Override
  public boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity) {
    return true;
  }

  @Override
  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(
      final String userId,
      final String searchString,
      final int maxResults,
      final boolean excludeUserGroups) {
    try {
      if (StringUtils.isBlank(searchString)) {
        return new IdentitySearchResultResponseDto(
            usersCache.getAllUsers().stream()
                .limit(maxResults)
                .map(this::mapToUserDto)
                .collect(toList()));
      } else {
        return new IdentitySearchResultResponseDto(
            usersCache.getAllUsers().stream()
                .filter(
                    cloudUser ->
                        cloudUser.getSearchableDtoFields().stream()
                            .map(Supplier::get)
                            .anyMatch(
                                field ->
                                    StringUtils.isNotBlank(field)
                                        && StringUtils.containsIgnoreCase(field, searchString)))
                .limit(maxResults)
                .map(this::mapToUserDto)
                .collect(toList()));
      }
    } catch (final OptimizeRuntimeException e) {
      LOG.warn("Failed retrieving users.", e);
      return new IdentitySearchResultResponseDto(Collections.emptyList());
    }
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getUsersById(final Set<String> userIds) {
    return usersCache.getUsersById(userIds).stream()
        .map(this::mapToUserDto)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .toList();
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getGroupsById(final Set<String> groupIds) {
    // Groups do not exist in SaaS
    return Collections.emptyList();
  }

  public List<UserDto> getUsersByEmail(final Set<String> emails) {
    try {
      return usersCache.getAllUsers().stream()
          .filter(
              cloudUser ->
                  StringUtils.containsAnyIgnoreCase(
                      cloudUser.getEmail(), emails.toArray(new String[0])))
          .map(this::mapToUserDto)
          .toList();
    } catch (final OptimizeRuntimeException e) {
      LOG.warn("Failed retrieving users.", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private UserDto mapToUserDto(final CloudUserDto cloudUser) {
    return new UserDto(
        cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail(), cloudUser.getRoles());
  }
}
