/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

public class EntityJoinRelation {

  private String name;
  private Long parent;

  public String getName() {
    return name;
  }

  public EntityJoinRelation setName(final String name) {
    this.name = name;
    return this;
  }

  public Long getParent() {
    return parent;
  }

  public EntityJoinRelation setParent(final Long parent) {
    this.parent = parent;
    return this;
  }

  public enum IdentityJoinRelationshipType {
    GROUP("group"),
    MEMBER("member");

    private final String type;

    IdentityJoinRelationshipType(final String type) {
      this.type = type;
    }

    public String getType() {
      return type;
    }
  }
}
