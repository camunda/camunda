/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;

public class MappingRuleEntity extends AbstractExporterEntity<MappingRuleEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  @BeforeVersion880 private Long key;
  @BeforeVersion880 private String mappingRuleId;
  @BeforeVersion880 private String claimName;
  @BeforeVersion880 private String claimValue;
  @BeforeVersion880 private String name;

  public MappingRuleEntity() {}

  public Long getKey() {
    return key;
  }

  public MappingRuleEntity setKey(final Long mappingRuleKey) {
    key = mappingRuleKey;
    return this;
  }

  public String getMappingRuleId() {
    return mappingRuleId;
  }

  public MappingRuleEntity setMappingRuleId(final String mappingRuleId) {
    this.mappingRuleId = mappingRuleId;
    return this;
  }

  public String getClaimName() {
    return claimName;
  }

  public MappingRuleEntity setClaimName(final String claimName) {
    this.claimName = claimName;
    return this;
  }

  public String getClaimValue() {
    return claimValue;
  }

  public MappingRuleEntity setClaimValue(final String claimValue) {
    this.claimValue = claimValue;
    return this;
  }

  public String getName() {
    return name;
  }

  public MappingRuleEntity setName(final String name) {
    this.name = name;
    return this;
  }
}
