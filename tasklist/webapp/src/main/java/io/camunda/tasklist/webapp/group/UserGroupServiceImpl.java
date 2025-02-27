/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.group;

import io.camunda.authentication.service.CamundaUserService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("consolidated-auth")
/**
 * Component definition for `UserGroupService` when the application is running under the
 * "consolidated-auth" profile.
 *
 * <p>- The `@Profile("consolidated-auth")` annotation ensures that this bean is only loaded when
 * the profile "consolidated-auth" is active.
 *
 * <p>This component provides the **main authorization logic** when consolidated authentication is
 * enabled.
 */
public class UserGroupServiceImpl implements UserGroupService {
  @Autowired private CamundaUserService camundaUserService;

  @Override
  public List<String> getUserGroups() {
    return camundaUserService.getCurrentUser().groups();
  }
}
