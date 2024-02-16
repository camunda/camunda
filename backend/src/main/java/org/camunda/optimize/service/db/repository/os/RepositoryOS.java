/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.os;

import org.camunda.optimize.service.db.repository.Repository;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class RepositoryOS implements Repository {
  @Override
  public String getDeleteByQueryActionName() {
    return "indices:data/write/delete/byquery";
  }

  @Override
  public String getUpdateByQueryActionName() {
    return "indices:data/write/update/byquery";
  }
}
