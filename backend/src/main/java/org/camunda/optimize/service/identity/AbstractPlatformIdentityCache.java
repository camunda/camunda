/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.engine.IdentityCacheConfiguration;
import org.springframework.scheduling.support.CronExpression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractPlatformIdentityCache extends AbstractIdentityCache {

  protected AbstractPlatformIdentityCache(final Supplier<IdentityCacheConfiguration> cacheConfigurationSupplier,
                                          final List<IdentityCacheSyncListener> identityCacheSyncListeners,
                                          final BackoffCalculator backoffCalculator) {
    super(cacheConfigurationSupplier, identityCacheSyncListeners, backoffCalculator);
  }

  @Override
  protected CronExpression evaluateCronExpression() {
    return CronExpression.parse(getCacheConfiguration().getCronTrigger());
  }

  protected List<UserDto> fetchUsersById(final EngineContext engineContext, final Collection<String> userIdBatch) {
    if (getCacheConfiguration().isIncludeUserMetaData()) {
      List<UserDto> users = engineContext.getUsersById(userIdBatch);
      List<String> usersNotInEngine = new ArrayList<>(userIdBatch);
      usersNotInEngine.removeIf(userId -> users.stream().anyMatch(user -> user.getId().equals(userId)));
      users.addAll(usersNotInEngine.stream().map(UserDto::new).collect(Collectors.toList()));
      return users;
    } else {
      return userIdBatch.stream().map(UserDto::new).collect(Collectors.toList());
    }
  }

  protected List<GroupDto> fetchGroupsById(final EngineContext engineContext, final Collection<String> groupIdBatch) {
    List<GroupDto> groups = engineContext.getGroupsById(groupIdBatch);
    List<String> groupsNotInEngine = new ArrayList<>(groupIdBatch);
    groupsNotInEngine.removeIf(groupId -> groups.stream().anyMatch(group -> group.getId().equals(groupId)));
    groups.addAll(groupsNotInEngine.stream().map(GroupDto::new).collect(Collectors.toList()));
    return groups;
  }

}
