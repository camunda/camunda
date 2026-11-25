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
import java.util.List;

public abstract class RoleIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ROLE_ID = "roleId";
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
  public static final String MEMBER_ID = "memberId";
  public static final String MEMBER_TYPE = "memberType";
  public static final String JOIN = "join";
  public static final String JOIN_PARENT = "role";
  public static final String JOIN_CHILD = "member";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ROLE_ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(DESCRIPTION, p -> p.text(t -> t))
        .properties(MEMBER_ID, p -> p.keyword(k -> k))
        .properties(MEMBER_TYPE, p -> p.keyword(k -> k))
        .properties(
            JOIN,
            p ->
                p.join(
                    j -> j.eagerGlobalOrdinals(true).relations(JOIN_PARENT, List.of(JOIN_CHILD))));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.ROLE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
