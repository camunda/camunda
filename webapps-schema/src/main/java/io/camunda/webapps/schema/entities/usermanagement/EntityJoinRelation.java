/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

public record EntityJoinRelation(String name, Long parent) {

  public static class EntityJoinRelationFactory {

    private final IdentityJoinRelationshipType parent;
    private final IdentityJoinRelationshipType child;

    public EntityJoinRelationFactory(
        final IdentityJoinRelationshipType parent, final IdentityJoinRelationshipType child) {
      this.parent = parent;
      this.child = child;
    }

    public EntityJoinRelation createParent() {
      return new EntityJoinRelation(parent.getType(), null);
    }

    public EntityJoinRelation createChild(final long parentKey) {
      return new EntityJoinRelation(child.getType(), parentKey);
    }
  }

  public enum IdentityJoinRelationshipType {
    GROUP("group"),
    ROLE("role"),
    TENANT("tenant"),
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
