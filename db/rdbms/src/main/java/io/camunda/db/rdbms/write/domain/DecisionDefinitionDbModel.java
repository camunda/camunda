/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record DecisionDefinitionDbModel(
    Long decisionDefinitionKey,
    String name,
    String decisionDefinitionId,
    String tenantId,
    int version,
    String decisionRequirementsId,
    Long decisionRequirementsKey,
    String decisionRequirementsName,
    int decisionRequirementsVersion) {

  // create a builder for this record extending ObjectBuilder
  public static class DecisionDefinitionDbModelBuilder
      implements ObjectBuilder<DecisionDefinitionDbModel> {

    private Long decisionDefinitionKey;
    private String name;
    private String decisionDefinitionId;
    private String tenantId;
    private int version;
    private String decisionRequirementsId;
    private Long decisionRequirementsKey;
    private String decisionRequirementsName;
    private int decisionRequirementsVersion;

    public DecisionDefinitionDbModelBuilder decisionDefinitionKey(
        final Long decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
      return this;
    }

    public DecisionDefinitionDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public DecisionDefinitionDbModelBuilder decisionDefinitionId(
        final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
      return this;
    }

    public DecisionDefinitionDbModelBuilder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public DecisionDefinitionDbModelBuilder version(final int version) {
      this.version = version;
      return this;
    }

    public DecisionDefinitionDbModelBuilder decisionRequirementsId(
        final String decisionRequirementsId) {
      this.decisionRequirementsId = decisionRequirementsId;
      return this;
    }

    public DecisionDefinitionDbModelBuilder decisionRequirementsKey(
        final Long decisionRequirementsKey) {
      this.decisionRequirementsKey = decisionRequirementsKey;
      return this;
    }

    public DecisionDefinitionDbModelBuilder decisionRequirementsName(
        final String decisionRequirementsName) {
      this.decisionRequirementsName = decisionRequirementsName;
      return this;
    }

    public DecisionDefinitionDbModelBuilder decisionRequirementsVersion(
        final int decisionRequirementsVersion) {
      this.decisionRequirementsVersion = decisionRequirementsVersion;
      return this;
    }

    @Override
    public DecisionDefinitionDbModel build() {
      return new DecisionDefinitionDbModel(
          decisionDefinitionKey,
          name,
          decisionDefinitionId,
          tenantId,
          version,
          decisionRequirementsId,
          decisionRequirementsKey,
          decisionRequirementsName,
          decisionRequirementsVersion);
    }
  }
}
