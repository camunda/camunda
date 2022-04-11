/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.security;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Conditional(CamundaCloudCondition.class)
@Component
@Slf4j
public class CloudApplicationAuthorizationService implements ApplicationAuthorizationService {

  @Override
  public boolean isUserAuthorizedToAccessOptimize(final String userId) {
    return true;
  }

  @Override
  public boolean isGroupAuthorizedToAccessOptimize(final String groupId) {
    return true;
  }

  @Override
  public List<String> getAuthorizedEnginesForUser(final String userId) {
    return Collections.emptyList();
  }

  @Override
  public List<String> getAuthorizedEnginesForGroup(final String groupId) {
    return Collections.emptyList();
  }

}
