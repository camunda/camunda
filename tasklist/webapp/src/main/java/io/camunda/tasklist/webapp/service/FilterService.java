/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.tasklist.webapp.service;

import io.camunda.tasklist.entities.FilterEntity;
import io.camunda.tasklist.entities.TaskFilterEntity;
import io.camunda.tasklist.store.FilterStore;
import io.camunda.tasklist.store.TaskFilterStore;
import io.camunda.tasklist.webapp.api.rest.v1.entities.AddFilterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FilterService {

  private static final Logger LOGGER = LoggerFactory.getLogger(FilterService.class);

  @Autowired
  private TaskFilterStore taskFilterStore;

  public TaskFilterEntity addFilter(final AddFilterRequest addFilterRequest){
    return taskFilterStore.persistFilter(addFilterRequest.toFilterEntity());
  }

}
