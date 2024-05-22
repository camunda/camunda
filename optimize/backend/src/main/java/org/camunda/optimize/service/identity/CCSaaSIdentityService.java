/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import static java.util.stream.Collectors.toList;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.rest.cloud.CCSaaSUserCache;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSIdentityService extends AbstractIdentityService {

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
      final String userId, final ContainerRequestContext requestContext) {
    return getUserById(userId);
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
    // Groups do not exist in SaaS
    return Optional.empty();
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getGroupsById(final Set<String> groupIds) {
    // Groups do not exist in SaaS
    return Collections.emptyList();
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
      log.warn("Failed retrieving users.", e);
      return new IdentitySearchResultResponseDto(Collections.emptyList());
    }
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
      log.warn("Failed retrieving users.", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<IdentityWithMetadataResponseDto> getUsersById(final Set<String> userIds) {
    return usersCache.getUsersById(userIds).stream()
        .map(this::mapToUserDto)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .toList();
  }

  @NotNull
  private UserDto mapToUserDto(final CloudUserDto cloudUser) {
    return new UserDto(
        cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail(), cloudUser.getRoles());
  }
}
