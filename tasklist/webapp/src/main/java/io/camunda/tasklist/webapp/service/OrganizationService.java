/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.tasklist.property.Auth0Properties;
import io.camunda.tasklist.property.TasklistProperties;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrganizationService {

  public static final String DEFAULT_ORGANIZATION = "null";

  @Autowired private TasklistProperties tasklistProperties;

  public String getOrganizationIfPresent() {
    return Optional.ofNullable(tasklistProperties.getAuth0())
        .map(Auth0Properties::getOrganization)
        .orElse(DEFAULT_ORGANIZATION);
  }
}
