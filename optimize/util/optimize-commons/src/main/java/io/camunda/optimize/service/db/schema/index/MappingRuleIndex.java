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

public abstract class MappingRuleIndex<TBuilder> extends DefaultIndexMappingCreator<TBuilder> {

  public static final int VERSION = 1;

  public static final String MAPPING_RULE_ID = "mappingRuleId";
  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";
  public static final String NAME = "name";

  @Override
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(MAPPING_RULE_ID, p -> p.keyword(k -> k))
        .properties(CLAIM_NAME, p -> p.keyword(k -> k))
        .properties(CLAIM_VALUE, p -> p.keyword(k -> k))
        .properties(NAME, p -> p.keyword(k -> k));
  }

  @Override
  public String getIndexName() {
    return DatabaseConstants.MAPPING_RULE_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }
}
