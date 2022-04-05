/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.dmn.definition;

import io.camunda.operate.entities.OperateZeebeEntity;
import java.util.Objects;

public class DecisionRequirementsEntity extends OperateZeebeEntity<DecisionRequirementsEntity> {

  private String decisionRequirementsId;
  private String name;
  private int version;
  private String xml;
  private String resourceName;

  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  public DecisionRequirementsEntity setDecisionRequirementsId(
      final String decisionRequirementsId) {
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
    return version == that.version &&
        Objects.equals(decisionRequirementsId, that.decisionRequirementsId) &&
        Objects.equals(name, that.name) &&
        Objects.equals(xml, that.xml) &&
        Objects.equals(resourceName, that.resourceName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), decisionRequirementsId, name, version, xml, resourceName);
  }
}
