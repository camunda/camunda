/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.users.dto.User;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.service.security.CCSMTokenService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.constants.RestConstants.OPTIMIZE_AUTHORIZATION;

@Component
@Slf4j
@Conditional(CCSMCondition.class)
public class CCSMIdentityService extends AbstractIdentityService {

  private final CCSMTokenService ccsmTokenService;
  private final Identity identity;

  public CCSMIdentityService(final ConfigurationService configurationService, final CCSMTokenService ccsmTokenService,
                             final Identity identity) {
    super(configurationService);
    this.ccsmTokenService = ccsmTokenService;
    this.identity = identity;
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    if (identity.users().isAvailable()) {
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      if (token.isPresent()) {
        try {
          return identity.users()
            .withAccessToken(token.get())
            .get(Collections.singletonList(userId))
            .stream().findFirst()
            .map(this::mapToUserDto);
        } catch (Exception e) {
          log.warn("Failed retrieving user by ID.", e);
          return Optional.empty();
        }
      } else {
        log.warn("Could not retrieve user because no user token present.");
        return Optional.empty();
      }
    } else {
      log.debug("Cannot search for user by ID because user search not available in Camunda identity.");
      return Optional.empty();
    }
  }

  @Override
  public Optional<UserDto> getCurrentUserById(final String userId, final ContainerRequestContext requestContext) {
    return Optional.ofNullable(requestContext.getCookies())
      .flatMap(cookies -> {
        final Cookie authorizationCookie = requestContext.getCookies().get(OPTIMIZE_AUTHORIZATION);
        return Optional.ofNullable(authorizationCookie)
          .map(cookie -> ccsmTokenService.getUserInfoFromToken(userId, authorizationCookie.getValue()));
      });
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
    // Identity permissions are handled by identity on each users() request where we supply the access token of the current
    // user.
    // Note "accessing identity" here means "accessing info about the other user/group"
    return true;
  }

  @Override
  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId, final String searchString,
                                                                   final int maxResults,
                                                                   final boolean excludeUserGroups) {
    if (identity.users().isAvailable()) {
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      if (token.isPresent()) {
        return new IdentitySearchResultResponseDto(searchForIdentityUsingSearchTerm(token.get(), searchString, maxResults));
      } else {
        log.warn("Could not search for identities because no user token present.");
        return new IdentitySearchResultResponseDto(Collections.emptyList());
      }
    } else {
      log.debug("Cannot search for identities because user search not available in Camunda identity.");
      return new IdentitySearchResultResponseDto(Collections.emptyList());
    }
  }

  public List<UserDto> getUsersByEmail(final List<String> emails) {
    if (identity.users().isAvailable()) {
      final Set<String> lowerCasedEmails = emails.stream().map(String::toLowerCase).collect(Collectors.toSet());
      final Optional<String> token = ccsmTokenService.getCurrentUserAuthToken();
      if (token.isPresent()) {
        return lowerCasedEmails.stream()
          .flatMap(email -> searchForIdentityUsingSearchTerm(
            token.get(),
            email,
            1 // we are only expecting 1 matching user per email
          ).stream())
          .filter(UserDto.class::isInstance)
          .map(UserDto.class::cast)
          .toList();
      } else {
        log.warn("Could not retrieve users by email because no user token present.");
        return Collections.emptyList();
      }
    } else {
      log.debug("Cannot search for users by email because no user search available in Camunda identity.");
      return Collections.emptyList();
    }
  }

  private List<IdentityWithMetadataResponseDto> searchForIdentityUsingSearchTerm(final String token, final String searchString,
                                                                                 final int maxResults) {
    if (identity.users().isAvailable()) {
      try {
        return identity.users()
          .withAccessToken(token)
          .search(searchString)
          .stream()
          .limit(maxResults)
          .map(this::mapToUserDto)
          .map(IdentityWithMetadataResponseDto.class::cast)
          .toList();
      } catch (Exception e) {
        log.warn("Failed searching for users with searchString {}.", searchString, e);
        return Collections.emptyList();
      }
    } else {
      log.debug("Cannot search for identity because no user search available in Camunda Identity.");
      return Collections.emptyList();
    }
  }

  @NotNull
  private UserDto mapToUserDto(final User user) {
    return new UserDto(user.getId(), user.getName(), user.getEmail(), Collections.emptyList());
  }

}
