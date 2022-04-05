/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities.listview;

public class ListViewJoinRelation {

  private String name;

  private Long parent;

  public ListViewJoinRelation() {
  }

  public ListViewJoinRelation(String name) {

    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Long getParent() {
    return parent;
  }

  public void setParent(Long parent) {
    this.parent = parent;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    ListViewJoinRelation that = (ListViewJoinRelation) o;

    if (name != null ? !name.equals(that.name) : that.name != null)
      return false;
    return parent != null ? parent.equals(that.parent) : that.parent == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (parent != null ? parent.hashCode() : 0);
    return result;
  }
}
