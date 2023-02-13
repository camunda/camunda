/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizationType;
import org.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.util.configuration.users.AuthorizedUserType.ALL;
import static org.camunda.optimize.service.util.configuration.users.AuthorizedUserType.SUPERUSER;

@Component
@Slf4j
public abstract class AbstractIdentityService implements ConfigurationReloadable {

  private static List<AuthorizationType> superUserAuthorizations;
  private static List<AuthorizationType> defaultUserAuthorizations;

  protected final ConfigurationService configurationService;

  protected AbstractIdentityService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    initializeAuthorizations(configurationService);
  }

  @Override
  public void reloadConfiguration(ApplicationContext context) {
    initializeAuthorizations(configurationService);
  }

  public abstract Optional<UserDto> getUserById(final String userId);

  public abstract Optional<GroupDto> getGroupById(final String groupId);

  public abstract List<GroupDto> getAllGroupsOfUser(final String userId);

  public abstract boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity);

  public abstract IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId,
                                                                            final String searchString,
                                                                            final int maxResults,
                                                                            final boolean excludeUserGroups);

  public boolean isSuperUserIdentity(final String userId) {
    return configurationService.getAuthConfiguration().getSuperUserIds().contains(userId) ||
      isInSuperUserGroup(userId);
  }

  public List<AuthorizationType> getUserAuthorizations(final String userId) {
    if (isSuperUserIdentity(userId)) {
      return ImmutableList.copyOf(superUserAuthorizations);
    }
    return ImmutableList.copyOf(defaultUserAuthorizations);
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
      // In case the user performing the query is unauthorized she cannot misuse this query to check if a user exists or
      // not. By mapping to null the user will get a 404 error message for the case that the searched user doesn't exist
      // as well as for the case that it does exist, but the user doesn't have the rights to see the result
      .map(identityDto -> isUserAuthorizedToAccessIdentity(userId, identityDto) ? identityDto : null);
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

  protected List<IdentityWithMetadataResponseDto> filterIdentitySearchResultByUserAuthorizations(
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

  private static void initializeAuthorizations(final ConfigurationService configurationService) {
    final List<AuthorizationType> initializedSuperUserAuthorizations = new ArrayList<>(List.of(AuthorizationType.TELEMETRY));
    final List<AuthorizationType> initializedDefaultUserAuthorizations = new ArrayList<>();

    initializeUserAuthorizationsForAuthorizationType(
      AuthorizationType.CSV_EXPORT,
      configurationService.getCsvConfiguration().getAuthorizedUserType(),
      initializedSuperUserAuthorizations,
      initializedDefaultUserAuthorizations
    );
    initializeUserAuthorizationsForAuthorizationType(
      AuthorizationType.ENTITY_EDITOR,
      configurationService.getEntityConfiguration().getAuthorizedUserType(),
      initializedSuperUserAuthorizations,
      initializedDefaultUserAuthorizations
    );
    superUserAuthorizations = ImmutableList.copyOf(initializedSuperUserAuthorizations);
    defaultUserAuthorizations = ImmutableList.copyOf(initializedDefaultUserAuthorizations);
  }

  private static void initializeUserAuthorizationsForAuthorizationType(final AuthorizationType authorizationType,
                                                                       final AuthorizedUserType authorizedUserType,
                                                                       final List<AuthorizationType> initializedSuperUserAuthorizations,
                                                                       final List<AuthorizationType> initializedDefaultUserAuthorizations) {
    if (authorizedUserType == ALL) {
      initializedSuperUserAuthorizations.add(authorizationType);
      initializedDefaultUserAuthorizations.add(authorizationType);
    } else {
      if (authorizedUserType == SUPERUSER) {
        initializedSuperUserAuthorizations.add(authorizationType);
      }
    }
  }

}
