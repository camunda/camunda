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

/**
 * POC: a role and who holds it, by mapping rule, user, group or client. Holding any role grants
 * access to Optimize; the role {@code admin} additionally grants administration.
 */
public abstract class AccessRoleIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String DESCRIPTION = "description";
  public static final String MAPPING_RULE_IDS = "mappingRuleIds";
  public static final String USER_IDS = "userIds";
  public static final String GROUP_IDS = "groupIds";
  public static final String CLIENT_IDS = "clientIds";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(ID, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k))
        .properties(DESCRIPTION, p -> p.keyword(k -> k))
        .properties(MAPPING_RULE_IDS, p -> p.keyword(k -> k))
        .properties(USER_IDS, p -> p.keyword(k -> k))
        .properties(GROUP_IDS, p -> p.keyword(k -> k))
        .properties(CLIENT_IDS, p -> p.keyword(k -> k));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.ACCESS_ROLE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
