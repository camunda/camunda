/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.db.schema.DefaultIndexMappingCreator;

public abstract class AuthorizationIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String OWNER_ID = "ownerId";
  public static final String OWNER_TYPE = "ownerType";
  public static final String RESOURCE_TYPE = "resourceType";
  public static final String RESOURCE_MATCHER = "resourceMatcher";
  public static final String RESOURCE_ID = "resourceId";
  public static final String RESOURCE_PROPERTY_NAME = "resourcePropertyName";
  public static final String PERMISSION_TYPES = "permissionTypes";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(OWNER_ID, p -> p.keyword(k -> k))
        .properties(OWNER_TYPE, p -> p.keyword(k -> k))
        .properties(RESOURCE_TYPE, p -> p.keyword(k -> k))
        .properties(RESOURCE_MATCHER, p -> p.short_(s -> s))
        .properties(RESOURCE_ID, p -> p.keyword(k -> k))
        .properties(RESOURCE_PROPERTY_NAME, p -> p.keyword(k -> k))
        .properties(PERMISSION_TYPES, p -> p.keyword(k -> k));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.AUTHORIZATION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
