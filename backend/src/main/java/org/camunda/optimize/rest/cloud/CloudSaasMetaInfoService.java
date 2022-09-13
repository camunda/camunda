/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
@RequiredArgsConstructor
public class CloudSaasMetaInfoService {
  private final CCSaaSOrganizationsClient organizationsClient;
  private final AccountsUserAccessTokenProvider accessTokenProvider;

  public Optional<String> getSalesPlanType() {
    final Optional<String> accessToken = accessTokenProvider.getCurrentUsersAccessToken();
    if (accessToken.isPresent()) {
      try {
        return organizationsClient.getSalesPlanType(accessToken.get());
      } catch (OptimizeRuntimeException e) {
        log.warn("Failed retrieving salesPlanType.", e);
        return Optional.empty();
      }
    } else {
      log.warn("No user access token found, will not retrieve salesPlanType");
      return Optional.empty();
    }
  }

}
