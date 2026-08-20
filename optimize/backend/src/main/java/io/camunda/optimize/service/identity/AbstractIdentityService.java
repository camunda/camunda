/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static io.camunda.optimize.dto.optimize.ReportConstants.API_IMPORT_OWNER_NAME;
import static io.camunda.optimize.service.util.configuration.users.AuthorizedUserType.ALL;

import com.google.common.collect.ImmutableList;
import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizationType;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public abstract class AbstractIdentityService implements ConfigurationReloadable {

  private static List<AuthorizationType> defaultUserAuthorizations;
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractIdentityService.class);

  protected final ConfigurationService configurationService;

  protected AbstractIdentityService(final ConfigurationService configurationService) {
    this.configurationService = configurationService;
    initializeAuthorizations(configurationService);
  }

  private static void initializeAuthorizations(final ConfigurationService configurationService) {
    final List<AuthorizationType> initializedDefaultUserAuthorizations = new ArrayList<>();
    initializeUserAuthorizationsForAuthorizationType(
        AuthorizationType.CSV_EXPORT,
        configurationService.getCsvConfiguration().getAuthorizedUserType(),
        initializedDefaultUserAuthorizations);
    initializeUserAuthorizationsForAuthorizationType(
        AuthorizationType.ENTITY_EDITOR,
        configurationService.getEntityConfiguration().getAuthorizedUserType(),
        initializedDefaultUserAuthorizations);
    defaultUserAuthorizations = ImmutableList.copyOf(initializedDefaultUserAuthorizations);
  }

  private static void initializeUserAuthorizationsForAuthorizationType(
      final AuthorizationType authorizationType,
      final AuthorizedUserType authorizedUserType,
      final List<AuthorizationType> initializedDefaultUserAuthorizations) {
    if (authorizedUserType == ALL) {
      initializedDefaultUserAuthorizations.add(authorizationType);
    }
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    initializeAuthorizations(configurationService);
  }

  public abstract Optional<UserDto> getUserById(final String userId);

  public abstract Optional<UserDto> getCurrentUserById(
      final String userId, final HttpServletRequest request);

  public abstract Optional<GroupDto> getGroupById(final String groupId);

  public abstract List<GroupDto> getAllGroupsOfUser(final String userId);

  public abstract boolean isUserAuthorizedToAccessIdentity(
      final String userId, final IdentityDto identity);

  public abstract IdentitySearchResultResponseDto searchForIdentitiesAsUser(
      final String userId,
      final String searchString,
      final int maxResults,
      final boolean excludeUserGroups);

  public List<IdentityWithMetadataResponseDto> getUsersById(final Set<String> userIds) {
    return userIds.stream()
        .map(this::getUserById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .toList();
  }

  public List<IdentityWithMetadataResponseDto> getGroupsById(final Set<String> groupIds) {
    return groupIds.stream()
        .map(this::getGroupById)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .toList();
  }

  public List<AuthorizationType> getEnabledAuthorizations() {
    return ImmutableList.copyOf(defaultUserAuthorizations);
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForId(
      final String userOrGroupId) {
    return getUserById(userOrGroupId)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .or(() -> getGroupById(userOrGroupId));
  }

  public Optional<IdentityWithMetadataResponseDto> getIdentityWithMetadataForIdAsUser(
      final String userId, final String userOrGroupId) {
    return getUserById(userOrGroupId)
        .map(IdentityWithMetadataResponseDto.class::cast)
        .or(() -> getGroupById(userOrGroupId))
        // In case the user performing the query is unauthorized she cannot misuse this query to
        // check if a user exists or
        // not. By mapping to null the user will get a 404 error message for the case that the
        // searched user doesn't exist
        // as well as for the case that it does exist, but the user doesn't have the rights to see
        // the result
        .map(
            identityDto ->
                isUserAuthorizedToAccessIdentity(userId, identityDto) ? identityDto : null);
  }

  public void validateUserAuthorizedToAccessRoleOrFail(
      final String userId, final IdentityDto identityDto) {
    if (!isUserAuthorizedToAccessIdentity(userId, identityDto)) {
      throw new ForbiddenException(
          String.format(
              "User with ID %s is not authorized to access identity with ID %s",
              userId, identityDto.getId()));
    }
  }

  public boolean doesIdentityExist(final IdentityDto identity) {
    return getIdentityWithMetadataForId(identity.getId()).isPresent();
  }

  public Optional<String> getIdentityNameById(final String identityId) {
    // For entities that have been created for instant preview dashboards, the identityId will be
    // "System User". In
    // that case, don't fetch, just return empty
    if (API_IMPORT_OWNER_NAME.equals(identityId)) {
      return Optional.empty();
    }
    final Optional<? extends IdentityWithMetadataResponseDto> identityDto =
        getIdentityWithMetadataForId(identityId);
    return identityDto.map(IdentityWithMetadataResponseDto::getName);
  }

  protected List<IdentityWithMetadataResponseDto> filterIdentitySearchResultByUserAuthorizations(
      final String userId, final IdentitySearchResultResponseDto result) {
    return result.getResult().stream()
        .filter(identity -> isUserAuthorizedToAccessIdentity(userId, identity))
        .toList();
  }
}
