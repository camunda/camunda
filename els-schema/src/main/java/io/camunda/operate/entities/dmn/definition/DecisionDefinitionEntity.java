/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.dmn.definition;

import io.camunda.operate.entities.OperateZeebeEntity;
import java.util.Objects;

public class DecisionDefinitionEntity extends OperateZeebeEntity<DecisionDefinitionEntity> {

  private String decisionId;
  private String name;
  private int version;
  private String decisionRequirementsId;
  private long decisionRequirementsKey;

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
    final DecisionDefinitionEntity that = (DecisionDefinitionEntity) o;
    return version == that.version &&
        decisionRequirementsKey == that.decisionRequirementsKey &&
        Objects.equals(decisionId, that.decisionId) &&
        Objects.equals(name, that.name) &&
        Objects.equals(decisionRequirementsId, that.decisionRequirementsId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), decisionId, name, version, decisionRequirementsId,
        decisionRequirementsKey);
  }
}
