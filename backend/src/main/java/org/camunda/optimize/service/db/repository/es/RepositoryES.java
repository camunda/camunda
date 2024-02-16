/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.repository.es;

import org.camunda.optimize.service.db.repository.Repository;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.index.reindex.DeleteByQueryAction;
import org.elasticsearch.index.reindex.UpdateByQueryAction;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class RepositoryES implements Repository {
  @Override
  public String getDeleteByQueryActionName() {
    return DeleteByQueryAction.NAME;
  }

  @Override
  public String getUpdateByQueryActionName() {
    return UpdateByQueryAction.NAME;
  }
}
