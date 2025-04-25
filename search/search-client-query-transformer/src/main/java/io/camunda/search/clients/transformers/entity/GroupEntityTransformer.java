/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.GroupEntity;
import java.util.Set;

public class GroupEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.usermanagement.GroupEntity, GroupEntity> {

  @Override
  public GroupEntity apply(
      final io.camunda.webapps.schema.entities.usermanagement.GroupEntity value) {
    final Set<String> memberSet =
        value.getMemberId() == null ? Set.of() : Set.of(value.getMemberId());
    return new GroupEntity(
        value.getKey(), value.getGroupId(), value.getName(), value.getDescription(), memberSet);
  }
}
