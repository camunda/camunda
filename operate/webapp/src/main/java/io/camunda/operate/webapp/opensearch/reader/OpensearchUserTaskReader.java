/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.opensearch.reader;

import static io.camunda.operate.store.opensearch.dsl.QueryDSL.matchAll;
import static io.camunda.operate.store.opensearch.dsl.QueryDSL.withTenantCheck;
import static io.camunda.operate.store.opensearch.dsl.RequestDSL.searchRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.UserTaskEntity;
import io.camunda.operate.schema.templates.UserTaskTemplate;
import io.camunda.operate.webapp.reader.UserTaskReader;
import java.util.List;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(OpensearchCondition.class)
@Component
public class OpensearchUserTaskReader extends OpensearchAbstractReader implements UserTaskReader {

  private final UserTaskTemplate userTaskTemplate;

  public OpensearchUserTaskReader(final UserTaskTemplate userTaskTemplate) {
    this.userTaskTemplate = userTaskTemplate;
  }

  @Override
  public List<UserTaskEntity> getUserTasks() {
    var request =
        searchRequestBuilder(userTaskTemplate.getAlias()).query(withTenantCheck(matchAll()));
    return richOpenSearchClient.doc().searchValues(request, UserTaskEntity.class);
  }
}
