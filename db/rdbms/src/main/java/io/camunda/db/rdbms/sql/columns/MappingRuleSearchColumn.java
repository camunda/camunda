/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.sql.columns;

import io.camunda.search.entities.MappingRuleEntity;

public enum MappingRuleSearchColumn implements SearchColumn<MappingRuleEntity> {
  MAPPING_RULE_ID("mappingRuleId"),
  MAPPING_RULE_KEY("mappingRuleKey"),
  CLAIM_NAME("claimName"),
  CLAIM_VALUE("claimValue"),
  NAME("name");

  private final String property;

  MappingRuleSearchColumn(final String property) {
    this.property = property;
  }

  @Override
  public String property() {
    return property;
  }

  @Override
  public Class<MappingRuleEntity> getEntityClass() {
    return MappingRuleEntity.class;
  }
}
