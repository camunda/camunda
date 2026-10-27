/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;

public record DecisionRequirementsDbModel(
    Long decisionRequirementsKey,
    String decisionRequirementsId,
    String name,
    String resourceName,
    Integer version,
    String xml,
    String tenantId) {

  public static final class Builder implements ObjectBuilder<DecisionRequirementsDbModel> {

    private Long decisionRequirementsKey;
    private String decisionRequirementsId;
    private String name;
    private String resourceName;
    private Integer version;
    private String xml;
    private String tenantId;

    public DecisionRequirementsDbModel.Builder decisionRequirementsKey(final Long value) {
      decisionRequirementsKey = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder decisionRequirementsId(final String value) {
      decisionRequirementsId = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder name(final String value) {
      name = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder resourceName(final String value) {
      resourceName = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder version(final Integer value) {
      version = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder xml(final String value) {
      xml = value;
      return this;
    }

    public DecisionRequirementsDbModel.Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    @Override
    public DecisionRequirementsDbModel build() {
      return new DecisionRequirementsDbModel(
          decisionRequirementsKey,
          decisionRequirementsId,
          name,
          resourceName,
          version,
          xml,
          tenantId);
    }
  }
}
