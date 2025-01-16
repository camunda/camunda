/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.api.v1.entities;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.webapps.schema.descriptors.index.DecisionIndex;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DecisionDefinition {

  // Used for index field search and sorting
  public static final String ID = DecisionIndex.ID,
      KEY = DecisionIndex.KEY,
      DECISION_ID = DecisionIndex.DECISION_ID,
      TENANT_ID = DecisionIndex.TENANT_ID,
      NAME = DecisionIndex.NAME,
      VERSION = DecisionIndex.VERSION,
      DECISION_REQUIREMENTS_ID = DecisionIndex.DECISION_REQUIREMENTS_ID,
      DECISION_REQUIREMENTS_KEY = DecisionIndex.DECISION_REQUIREMENTS_KEY,
      DECISION_REQUIREMENTS_NAME = "decisionRequirementsName",
      DECISION_REQUIREMENTS_VERSION = "decisionRequirementsVersion";

  private String id;
  private Long key;
  private String decisionId;
  private String name;
  private Integer version;
  private String decisionRequirementsId;
  private Long decisionRequirementsKey;
  private String decisionRequirementsName;
  private Integer decisionRequirementsVersion;
  private String tenantId;

  public String getId() {
    return id;
  }

  public DecisionDefinition setId(String id) {
    this.id = id;
    return this;
  }

  public Long getKey() {
    return key;
  }

  public DecisionDefinition setKey(long key) {
    this.key = key;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionDefinition setDecisionId(String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionDefinition setName(String name) {
    this.name = name;
    return this;
  }

  public Integer getVersion() {
    return version;
  }

  public DecisionDefinition setVersion(int version) {
    this.version = version;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionDefinition setDecisionRequirementsId(String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public Long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionDefinition setDecisionRequirementsKey(long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsName() {
    return decisionRequirementsName;
  }

  public DecisionDefinition setDecisionRequirementsName(String decisionRequirementsName) {
    this.decisionRequirementsName = decisionRequirementsName;
    return this;
  }

  public Integer getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  public DecisionDefinition setDecisionRequirementsVersion(int decisionRequirementsVersion) {
    this.decisionRequirementsVersion = decisionRequirementsVersion;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public DecisionDefinition setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id,
        key,
        decisionId,
        name,
        version,
        decisionRequirementsId,
        decisionRequirementsKey,
        decisionRequirementsName,
        decisionRequirementsVersion,
        tenantId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionDefinition that = (DecisionDefinition) o;
    return Objects.equals(id, that.id)
        && Objects.equals(key, that.key)
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(name, that.name)
        && Objects.equals(version, that.version)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(decisionRequirementsKey, that.decisionRequirementsKey)
        && Objects.equals(decisionRequirementsName, that.decisionRequirementsName)
        && Objects.equals(decisionRequirementsVersion, that.decisionRequirementsVersion)
        && Objects.equals(tenantId, that.tenantId);
  }

  @Override
  public String toString() {
    return "DecisionDefinition{"
        + "id='"
        + id
        + '\''
        + ", key="
        + key
        + ", decisionId='"
        + decisionId
        + '\''
        + ", name='"
        + name
        + '\''
        + ", version="
        + version
        + ", decisionRequirementsId='"
        + decisionRequirementsId
        + '\''
        + ", decisionRequirementsKey="
        + decisionRequirementsKey
        + ", decisionRequirementsName='"
        + decisionRequirementsName
        + '\''
        + ", decisionRequirementsVersion="
        + decisionRequirementsVersion
        + ", tenantId='"
        + tenantId
        + '\''
        + '}';
  }
}
