/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.dmn;

import java.util.Objects;

public class DecisionInstanceInputEntity {

  private String id;
  private String name;
  private String value;

  public String getId() {
    return id;
  }

  public DecisionInstanceInputEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public DecisionInstanceInputEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public DecisionInstanceInputEntity setValue(final String value) {
    this.value = value;
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
    final DecisionInstanceInputEntity that = (DecisionInstanceInputEntity) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(name, that.name) &&
        Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value);
  }
}
