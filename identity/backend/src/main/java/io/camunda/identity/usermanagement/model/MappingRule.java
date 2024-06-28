/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "mapping_rules",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"name", "claim_name", "claim_value", "type", "operator", "mapped_user_id"})
    })
public class MappingRule {

  @Id private String name;
  @NotNull private String claimName;
  @NotNull private String claimValue;

  @NotNull
  @Enumerated(EnumType.STRING)
  private Operator operator;

  @OneToOne
  @JoinColumn(name = "mapped_user_id")
  private MappedUser mappedUser;

  public MappingRule() {}

  public MappingRule(
      final String name, final String claimName, final String claimValue, final Operator operator) {
    this.name = name;
    this.claimName = claimName;
    this.claimValue = claimValue;
    this.operator = operator;
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

  public MappedUser getMappedUser() {
    return mappedUser;
  }

  public void setMappedUser(final MappedUser mappedUser) {
    this.mappedUser = mappedUser;
  }
}
