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
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Objects;

public class DecisionRequirementsEntity
    implements ExporterEntity<DecisionRequirementsEntity>, TenantOwned {

  @BeforeVersion880 private String id;
  @BeforeVersion880 private long key;
  @BeforeVersion880 private String decisionRequirementsId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private int version;
  @BeforeVersion880 private String xml;
  @BeforeVersion880 private String resourceName;
  @BeforeVersion880 private String tenantId = DEFAULT_TENANT_IDENTIFIER;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public DecisionRequirementsEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public long getKey() {
    return key;
  }

  public DecisionRequirementsEntity setKey(final long key) {
    this.key = key;
    return this;
  }

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionRequirementsEntity setDecisionRequirementsId(final String decisionRequirementsId) {
    this.decisionRequirementsId = decisionRequirementsId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionRequirementsEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public int getVersion() {
    return version;
  }

  public DecisionRequirementsEntity setVersion(final int version) {
    this.version = version;
    return this;
  }

  public String getXml() {
    return xml;
  }

  public DecisionRequirementsEntity setXml(final String xml) {
    this.xml = xml;
    return this;
  }

  public String getResourceName() {
    return resourceName;
  }

  public DecisionRequirementsEntity setResourceName(final String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  public DecisionRequirementsEntity setTenantId(final String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        id, key, decisionRequirementsId, name, version, xml, resourceName, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DecisionRequirementsEntity that = (DecisionRequirementsEntity) o;
    return Objects.equals(id, that.id)
        && key == that.key
        && version == that.version
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(name, that.name)
        && Objects.equals(xml, that.xml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId);
  }
}
