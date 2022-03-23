/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.cloud.CCSaaSUserClient;
import org.camunda.optimize.service.SearchableIdentityCache;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSaaSCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@Conditional(CCSaaSCondition.class)
public class CCSaaSUserIdentityCache extends AbstractIdentityCache implements UserIdentityCache {

  private final CCSaaSUserClient userClient;

  protected CCSaaSUserIdentityCache(final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                    final ConfigurationService configurationService,
                                    final CCSaaSUserClient userClient,
                                    final BackoffCalculator backoffCalculator) {
    super(configurationService::getUserIdentityCacheConfiguration, identityCacheSyncListeners, backoffCalculator);
    this.userClient = userClient;
  }

  @Override
  protected String getCacheLabel() {
    return "Cloud user";
  }

  @Override
  protected CronExpression evaluateCronExpression() {
    final String cronTrigger = getCacheConfiguration().getCronTrigger();
    String[] tokenisedExpression = cronTrigger.split("\\s+");
    // To distribute the requests more evenly amongst cloud clusters, we select a random second and minute for
    // the cron expression
    tokenisedExpression[0] = String.valueOf(RandomUtils.nextInt(0, 60));
    tokenisedExpression[1] = String.valueOf(RandomUtils.nextInt(0, 60));
    return CronExpression.parse(String.join(" ", tokenisedExpression));
  }

  @Override
  protected void populateCache(final SearchableIdentityCache newIdentityCache) {
    final List<UserDto> users = userClient.fetchAllCloudUsers()
      .stream()
      .map(cloudUser -> new UserDto(cloudUser.getUserId(), cloudUser.getName(), cloudUser.getEmail()))
      .collect(Collectors.toList());
    newIdentityCache.addIdentities(users);
  }

}
