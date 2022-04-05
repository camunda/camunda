/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.dmn;

import java.util.Objects;

public class DecisionInstanceOutputEntity {

  private String id;
  private String name;
  private String value;
  private String ruleId;
  private int ruleIndex;

  public String getId() {
    return id;
  }

  public DecisionInstanceOutputEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionInstanceOutputEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DecisionInstanceOutputEntity setValue(final String value) {
    this.value = value;
    return this;
  }

  public String getRuleId() {
    return ruleId;
  }

  public DecisionInstanceOutputEntity setRuleId(final String ruleId) {
    this.ruleId = ruleId;
    return this;
  }

  public int getRuleIndex() {
    return ruleIndex;
  }

  public DecisionInstanceOutputEntity setRuleIndex(final int ruleIndex) {
    this.ruleIndex = ruleIndex;
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
    final DecisionInstanceOutputEntity that = (DecisionInstanceOutputEntity) o;
    return ruleIndex == that.ruleIndex &&
        Objects.equals(id, that.id) &&
        Objects.equals(name, that.name) &&
        Objects.equals(value, that.value) &&
        Objects.equals(ruleId, that.ruleId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, ruleId, ruleIndex);
  }
}
