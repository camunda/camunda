/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.dto;

public class MappingRule {

  protected MappingRuleType type;
  protected String name;
  protected String claimName;
  protected String claimValue;
  protected Operator operator;

  public MappingRule() {}

  public MappingRule(
      final MappingRuleType type,
      final String name,
      final String claimName,
      final String claimValue,
      final Operator operator) {
    this.type = type;
    this.name = name;
    this.claimName = claimName;
    this.claimValue = claimValue;
    this.operator = operator;
  }

  public MappingRuleType getType() {
    return type;
  }

  public void setType(final MappingRuleType type) {
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getClaimName() {
    return claimName;
  }

  public void setClaimName(final String claimName) {
    this.claimName = claimName;
  }

  public String getClaimValue() {
    return claimValue;
  }

  public void setClaimValue(final String claimValue) {
    this.claimValue = claimValue;
  }

  public Operator getOperator() {
    return operator;
  }

  public void setOperator(final Operator operator) {
    this.operator = operator;
  }

  public enum MappingRuleType {
    ROLE,
    TENANT,
    GROUP;
  }

  public enum Operator {
    CONTAINS,
    EQUALS;
  }
}
