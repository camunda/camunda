/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import java.util.Objects;
import java.util.StringJoiner;

public class VariableResponse {

  private String id;
  private String name;
  private String value;

  public String getId() {
    return id;
  }

  public VariableResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableResponse setValue(String value) {
    this.value = value;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableResponse that = (VariableResponse) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", VariableResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("value='" + value + "'")
        .toString();
  }
}
