/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.os;

import io.camunda.optimize.service.db.repository.Repository;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class RepositoryOS implements Repository {
  private static final String DELETE_BY_QUERY_ACTION_NAME = "indices:data/write/delete/byquery";
  private static final String UPDATE_BY_QUERY_ACTION_NAME = "indices:data/write/update/byquery";

  @Override
  public String getDeleteByQueryActionName() {
    return DELETE_BY_QUERY_ACTION_NAME;
  }

  @Override
  public String getUpdateByQueryActionName() {
    return UPDATE_BY_QUERY_ACTION_NAME;
  }
}
