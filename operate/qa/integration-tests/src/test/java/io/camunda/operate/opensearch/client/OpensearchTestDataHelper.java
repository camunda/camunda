/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch.client;

import static io.camunda.operate.store.opensearch.dsl.RequestDSL.indexRequestBuilder;

import io.camunda.operate.conditions.OpensearchCondition;
import io.camunda.operate.entities.UserEntity;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.index.OperateUserIndex;
import org.opensearch.client.opensearch._types.Refresh;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpensearchCondition.class)
public class OpensearchTestDataHelper {
  @Autowired RichOpenSearchClient richOpenSearchClient;

  @Autowired OperateUserIndex operateUserIndex;

  public void addUser(final String id, final String name, final String password) {
    final var user = new UserEntity();
    user.setUserId(id);
    user.setDisplayName(name);
    user.setPassword(password);

    final var indexRequestBuilder =
        indexRequestBuilder(operateUserIndex.getFullQualifiedName())
            .id(id)
            .document(user)
            .refresh(Refresh.True);

    richOpenSearchClient.doc().index(indexRequestBuilder);
  }
}
