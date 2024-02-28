/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.opensearch.client;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.schema.indices.UserIndex;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchTestDataHelper {
  @Autowired RichOpenSearchClient richOpenSearchClient;

  @Autowired UserIndex userIndex;

  public void addUser(String id, String name, String password) {
    var user = new UserEntity();
    user.setUserId(id);
    user.setDisplayName(name);
    user.setPassword(password);

    var indexRequestBuilder =
        indexRequestBuilder(userIndex.getFullQualifiedName())
            .id(id)
            .document(user)
            .refresh(Refresh.True);

    richOpenSearchClient.doc().index(indexRequestBuilder);
  }
}
