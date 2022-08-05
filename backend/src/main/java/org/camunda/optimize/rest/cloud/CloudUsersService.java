/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotAuthorizedException;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
@RequiredArgsConstructor
public class CloudUsersService {

  private static final String ERROR_MISSING_ACCESS_TOKEN = "Missing user access token for service access.";

  private final CCSaaSUserClient userClient;
  private final AccountsUserAccessTokenProvider accessTokenProvider;

  public Optional<CloudUserDto> getUserById(final String userId) {
    return accessTokenProvider.getCurrentUsersAccessToken()
      .map(accessToken -> userClient.getCloudUserById(userId, accessToken))
      .orElseThrow(() -> new NotAuthorizedException(ERROR_MISSING_ACCESS_TOKEN));
  }

  public List<CloudUserDto> fetchAllUsers() {
    return accessTokenProvider.getCurrentUsersAccessToken()
      .map(userClient::fetchAllCloudUsers)
      .orElseThrow(() -> new NotAuthorizedException(ERROR_MISSING_ACCESS_TOKEN));
  }
}
