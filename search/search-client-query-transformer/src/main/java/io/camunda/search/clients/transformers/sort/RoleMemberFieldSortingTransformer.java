/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.RoleIndex.MEMBER_ID;
import static io.camunda.webapps.schema.descriptors.index.RoleIndex.MEMBER_TYPE;

public class RoleMemberFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "id" -> MEMBER_ID;
      case "entityType" -> MEMBER_TYPE;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
