/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.cloud.CloudUserDto;
import org.camunda.optimize.rest.cloud.CloudUserClient;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.CamundaCloudCondition;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@Conditional(CamundaCloudCondition.class)
public class CloudUserIdentityCacheService extends AbstractIdentityCacheService implements UserIdentityCache {

  private final CloudUserClient cloudUserClient;

  protected CloudUserIdentityCacheService(final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                          final ConfigurationService configurationService,
                                          final CloudUserClient cloudUserClient,
                                          final BackoffCalculator backoffCalculator) {
    super(configurationService::getUserIdentityCacheConfiguration, identityCacheSyncListeners, backoffCalculator);
    this.cloudUserClient = cloudUserClient;
  }

  @Override
  protected String getCacheLabel() {
    return "Cloud user";
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    final List<UserDto> users = cloudUserClient.fetchAllCloudUsers()
      .stream()
      .map(cloudUser -> new UserDto(cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail()))
      .collect(Collectors.toList());
    newIdentityCache.addIdentities(users);
  }

}
