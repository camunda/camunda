/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.se.SearchEngineUserDetailsService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@DependsOn("searchEngineSchemaInitializer")
@Profile("!test")
public class StartupBean {

  private static final Logger LOGGER = LoggerFactory.getLogger(StartupBean.class);

  @Autowired(required = false)
  private SearchEngineUserDetailsService searchEngineUserDetailsService;

  @Autowired private TasklistProperties tasklistProperties;

  @PostConstruct
  public void initApplication() {
    if (searchEngineUserDetailsService != null) {
      LOGGER.info("INIT: Create users if not exists ...");
      searchEngineUserDetailsService.initializeUsers();
    }
    LOGGER.info("INIT: DONE");
  }
}
