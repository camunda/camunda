/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.rest.cloud.CloudUsersService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import javax.validation.constraints.NotNull;
import javax.ws.rs.container.ContainerRequestContext;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
public class CCSaaSIdentityService extends AbstractIdentityService {

  private final CloudUsersService usersService;

  public CCSaaSIdentityService(final ConfigurationService configurationService, final CloudUsersService usersService) {
    super(configurationService);
    this.usersService = usersService;
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    return usersService.getUserById(userId).map(this::mapToUserDto);
  }

  @Override
  public Optional<UserDto> getUserById(final String userId, final ContainerRequestContext requestContext) {
    return getUserById(userId);
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
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
  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                   final String searchString,
                                                                   final int maxResults,
                                                                   final boolean excludeUserGroups) {
    final String lowerCasedSearchString = searchString.toLowerCase();
    try {
      final List<IdentityWithMetadataResponseDto> users = usersService.getAllUsers()
        .stream()
        .filter(cloudUser -> cloudUser.getName().toLowerCase().contains(lowerCasedSearchString)
          || cloudUser.getEmail().toLowerCase().contains(lowerCasedSearchString))
        .limit(maxResults)
        .map(this::mapToUserDto)
        .collect(Collectors.toList());
      return new IdentitySearchResultResponseDto(0, users);
    } catch (OptimizeRuntimeException e) {
      log.warn("Failed retrieving users.", e);
      return new IdentitySearchResultResponseDto(0, Collections.emptyList());
    }
  }

  public List<UserDto> getUsersByEmail(final List<String> emails) {
    final Set<String> lowerCasedEmails = emails.stream().map(String::toLowerCase).collect(Collectors.toSet());
    try {
      return usersService.getAllUsers()
        .stream()
        .filter(cloudUser -> lowerCasedEmails.contains(cloudUser.getEmail()))
        .map(this::mapToUserDto)
        .collect(Collectors.toList());
    } catch (OptimizeRuntimeException e) {
      log.warn("Failed retrieving users.", e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private UserDto mapToUserDto(final CloudUserDto cloudUser) {
    return new UserDto(cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail(), cloudUser.getRoles());
  }

}
