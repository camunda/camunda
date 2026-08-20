/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.repository.es;

import io.camunda.optimize.service.db.repository.Repository;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class RepositoryES implements Repository {
  @Override
  public String getDeleteByQueryActionName() {
    return "indices:data/write/delete/byquery";
  }

  @Override
  public String getUpdateByQueryActionName() {
    return "indices:data/write/update/byquery";
  }
}
