/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.identity;

import static io.camunda.tasklist.property.IdentityProperties.FULL_GROUP_ACCESS;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultIdentityAuthorizationServiceImpl implements IdentityAuthorizationService {
  @Override
  public List<String> getUserGroups() {
    final List<String> defaultGroups = new ArrayList<>();
    defaultGroups.add(FULL_GROUP_ACCESS);
    return defaultGroups;
  }
}
