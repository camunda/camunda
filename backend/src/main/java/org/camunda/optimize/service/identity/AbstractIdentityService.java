/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

@Component
@Slf4j
public abstract class AbstractIdentityService {

  private static final List<AuthorizationType> SUPERUSER_AUTHORIZATIONS =
    ImmutableList.copyOf(AuthorizationType.values());

  protected final ConfigurationService configurationService;
  protected final UserIdentityCache syncedIdentityCache;

  protected AbstractIdentityService(final ConfigurationService configurationService,
                                    final UserIdentityCache syncedIdentityCache) {
    this.configurationService = configurationService;
    this.syncedIdentityCache = syncedIdentityCache;
  }

  public abstract Optional<UserDto> getUserById(final String userId);

  public abstract Optional<GroupDto> getGroupById(final String groupId);

  public abstract List<GroupDto> getAllGroupsOfUser(final String userId);

  public abstract boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity);

  public void addIdentity(final IdentityWithMetadataResponseDto identity) {
    syncedIdentityCache.addIdentity(identity);
  }

  public boolean isSuperUserIdentity(final String userId) {
    return configurationService.getAuthConfiguration().getSuperUserIds().contains(userId) ||
      isInSuperUserGroup(userId);
  }

  public List<AuthorizationType> getUserAuthorizations(final String userId) {
    if (isSuperUserIdentity(userId)) {
      return SUPERUSER_AUTHORIZATIONS;
    }
    return Collections.emptyList();
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForId(final String userOrGroupId) {
    return getUserById(userOrGroupId)
      .map(IdentityWithMetadataResponseDto.class::cast)
      .or(() -> getGroupById(userOrGroupId));
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForIdAsUser(final String userId,
                                                                                      final String userOrGroupId) {
    return getUserById(userOrGroupId)
      .map(IdentityWithMetadataResponseDto.class::cast)
      .or(() -> getGroupById(userOrGroupId))
      .map(identityDto -> {
        if (!isUserAuthorizedToAccessIdentity(userId, identityDto)) {
          throw new ForbiddenException(String.format(
            "The user with ID %s is not authorized to access the identity with ID %s", userId, userOrGroupId
          ));
        }
        return identityDto;
      });
  }

  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                   final String searchString,
                                                                   final int maxResults) {
    final List<IdentityWithMetadataResponseDto> filteredIdentities = new ArrayList<>();
    IdentitySearchResultResponseDto result = syncedIdentityCache.searchIdentities(
      searchString, IdentityType.values(), maxResults
    );
    while (!result.getResult().isEmpty()
      && filteredIdentities.size() < maxResults) {
      // continue searching until either the maxResult number of hits has been found or
      // the end of the cache has been reached
      filteredIdentities.addAll(filterIdentitySearchResultByUserAuthorizations(userId, result));
      result = syncedIdentityCache.searchIdentitiesAfter(searchString, IdentityType.values(), maxResults, result);
    }
    return new IdentitySearchResultResponseDto(result.getTotal(), filteredIdentities);
  }

  public void validateUserAuthorizedToAccessRoleOrFail(final String userId, final IdentityDto identityDto) {
    if (!isUserAuthorizedToAccessIdentity(userId, identityDto)) {
      throw new ForbiddenException(
        String.format(
          "User with ID %s is not authorized to access identity with ID %s",
          userId,
          identityDto.getId()
        )
      );
    }
  }

  public boolean doesIdentityExist(final IdentityDto identity) {
    return getIdentityWithMetadataForId(identity.getId()).isPresent();
  }

  public Optional<String> getIdentityNameById(final String identityId) {
    Optional<? extends IdentityWithMetadataResponseDto> identityDto = getIdentityWithMetadataForId(identityId);
    return identityDto.map(IdentityWithMetadataResponseDto::getName);
  }

  private List<IdentityWithMetadataResponseDto> filterIdentitySearchResultByUserAuthorizations(
    final String userId,
    final IdentitySearchResultResponseDto result) {
    return result.getResult()
      .stream()
      .filter(identity -> isUserAuthorizedToAccessIdentity(userId, identity))
      .collect(toList());
  }

  private boolean isInSuperUserGroup(final String userId) {
    final List<String> authorizedGroupIds =
      configurationService.getAuthConfiguration().getSuperGroupIds();
    return getAllGroupsOfUser(userId)
      .stream()
      .map(IdentityDto::getId)
      .anyMatch(authorizedGroupIds::contains);
  }

}
