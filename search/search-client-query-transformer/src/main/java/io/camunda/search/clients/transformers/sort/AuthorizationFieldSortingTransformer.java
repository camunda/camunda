/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_MATCHER;
import static io.camunda.webapps.schema.descriptors.index.AuthorizationIndex.RESOURCE_TYPE;

public class AuthorizationFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "ownerId" -> OWNER_ID;
      case "ownerType" -> OWNER_TYPE;
      case "resourceType" -> RESOURCE_TYPE;
      case "resourceMatcher" -> RESOURCE_MATCHER;
      case "resourceId" -> RESOURCE_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return ID;
  }
}
