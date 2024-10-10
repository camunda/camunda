/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.db.indices;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public abstract class UserTestIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  private int version = 1;

  @Override
  public String getIndexName() {
    return "users";
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties("password", p -> p.keyword(k -> k))
        .properties("username", p -> p.keyword(k -> k));
  }
}
