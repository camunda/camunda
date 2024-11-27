/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record EntityJoinRelation(String name, Long parent) {

  @JsonIgnore
  public String getRouting() {
    return parent != null ? parent.toString() : null;
  }

  public static class EntityJoinRelationFactory {

    private final String parentName;
    private final String childName;

    public EntityJoinRelationFactory(final String parentName, final String childName) {
      this.parentName = parentName;
      this.childName = childName;
    }

    public EntityJoinRelation createParent() {
      return new EntityJoinRelation(parentName, null);
    }

    public EntityJoinRelation createChild(final long parentKey) {
      return new EntityJoinRelation(childName, parentKey);
    }

    public String getChildName() {
      return childName;
    }

    public String getChildKey(final long parentKey, final long childKey) {
      return "%s-%s/%s-%s".formatted(parentName, childName, parentKey, childKey);
    }
  }
}
