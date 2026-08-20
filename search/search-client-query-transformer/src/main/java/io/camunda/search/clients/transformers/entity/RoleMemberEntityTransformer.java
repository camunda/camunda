/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.entity;

import io.camunda.search.clients.transformers.ServiceTransformer;
import io.camunda.search.entities.RoleMemberEntity;

public class RoleMemberEntityTransformer
    implements ServiceTransformer<
        io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity, RoleMemberEntity> {

  @Override
  public RoleMemberEntity apply(
      final io.camunda.webapps.schema.entities.usermanagement.RoleMemberEntity source) {
    return new RoleMemberEntity(source.getMemberId(), source.getMemberType());
  }
}
