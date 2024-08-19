/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(CamundaCloudCondition.class)
@Component
public class CloudApplicationAuthorizationService implements ApplicationAuthorizationService {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CloudApplicationAuthorizationService.class);

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
