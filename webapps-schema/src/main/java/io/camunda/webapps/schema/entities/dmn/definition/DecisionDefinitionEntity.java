/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.dmn.definition;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.SinceVersion;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class DecisionDefinitionEntity
    implements ExporterEntity<DecisionDefinitionEntity>, TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private String decisionId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private int version;
  @BeforeVersion880 private String decisionRequirementsId;
  @BeforeVersion880 private long decisionRequirementsKey;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private String decisionRequirementsName;

  @SinceVersion(value = "8.9.0", requireDefault = false)
  private int decisionRequirementsVersion;

  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DecisionDefinitionEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public DecisionDefinitionEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getDecisionId() {
    return decisionId;
  }

  public DecisionDefinitionEntity setDecisionId(final String decisionId) {
    this.decisionId = decisionId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionDefinitionEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public DecisionDefinitionEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionDefinitionEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  public DecisionDefinitionEntity setDecisionRequirementsKey(final long decisionRequirementsKey) {
    this.decisionRequirementsKey = decisionRequirementsKey;
    return this;
  }

  public String getDecisionRequirementsName() {
    return decisionRequirementsName;
  }

  public DecisionDefinitionEntity setDecisionRequirementsName(
      final String decisionRequirementsName) {
    this.decisionRequirementsName = decisionRequirementsName;
    return this;
  }

  public int getDecisionRequirementsVersion() {
    return decisionRequirementsVersion;
  }

  public DecisionDefinitionEntity setDecisionRequirementsVersion(
      final int decisionRequirementsVersion) {
    this.decisionRequirementsVersion = decisionRequirementsVersion;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public DecisionDefinitionEntity setTenantId(final String tenantId) {
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
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionDefinitionEntity that = (DecisionDefinitionEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && version == that.version
        && decisionRequirementsKey == that.decisionRequirementsKey
        && Objects.equals(decisionId, that.decisionId)
        && Objects.equals(name, that.name)
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(decisionRequirementsName, that.decisionRequirementsName)
        && decisionRequirementsVersion == that.decisionRequirementsVersion
        && Objects.equals(tenantId, that.tenantId);
  }
}
