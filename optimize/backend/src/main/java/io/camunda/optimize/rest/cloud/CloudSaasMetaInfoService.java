/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.cloud;

import io.camunda.optimize.dto.optimize.query.ui_configuration.AppName;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(CCSaaSCondition.class)
@RequiredArgsConstructor
public class CloudSaasMetaInfoService {
  private final CCSaaSOrganizationsClient organizationsClient;
  private final AccountsUserAccessTokenProvider accessTokenProvider;
  private final CCSaasClusterClient clusterClient;

  public Optional<String> getSalesPlanType() {
    final Optional<String> accessToken = getCurrentUserServiceToken();
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

  public Map<AppName, String> getWebappsLinks() {
    final Optional<String> accessToken = getCurrentUserServiceToken();
    if (accessToken.isPresent()) {
      try {
        return clusterClient.getWebappLinks(accessToken.get());
      } catch (OptimizeRuntimeException e) {
        log.warn("Failed retrieving webapp links  .", e);
        return Collections.emptyMap();
      }
    } else {
      log.warn("No user access token found, will not retrieve links to other webapps.");
      return Collections.emptyMap();
    }
  }

  public Optional<String> getCurrentUserServiceToken() {
    return accessTokenProvider.getCurrentUsersAccessToken();
  }
}
