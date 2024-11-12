/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.operate.dmn.definition;

import io.camunda.webapps.schema.entities.operate.OperateZeebeEntity;
import java.util.Objects;

public class DecisionRequirementsEntity extends OperateZeebeEntity<DecisionRequirementsEntity> {

  private String decisionRequirementsId;
  private String name;
  private int version;
  private String xml;
  private String resourceName;
  private String tenantId = DEFAULT_TENANT_ID;

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
        super.hashCode(), decisionRequirementsId, name, version, xml, resourceName, tenantId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final DecisionRequirementsEntity that = (DecisionRequirementsEntity) o;
    return version == that.version
        && Objects.equals(decisionRequirementsId, that.decisionRequirementsId)
        && Objects.equals(name, that.name)
        && Objects.equals(xml, that.xml)
        && Objects.equals(resourceName, that.resourceName)
        && Objects.equals(tenantId, that.tenantId);
  }
}
