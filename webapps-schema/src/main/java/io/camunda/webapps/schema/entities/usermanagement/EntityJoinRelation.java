/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.BeforeVersion880;
import io.camunda.webapps.schema.entities.JoinRelationshipType;
import io.camunda.zeebe.protocol.record.value.EntityType;

public record EntityJoinRelation(@BeforeVersion880 String name, @BeforeVersion880 String parent) {

  public static class EntityJoinRelationFactory<T extends JoinRelationshipType> {

    private final T parent;
    private final T child;

    public EntityJoinRelationFactory(final T parent, final T child) {
      this.parent = parent;
      this.child = child;
    }

    public EntityJoinRelation createParent() {
      return new EntityJoinRelation(parent.getType(), null);
    }

    public EntityJoinRelation createChild(final String parentId) {
      return new EntityJoinRelation(child.getType(), createParentId(parentId));
    }

    public String createParentId(final String parentId) {
      return parent.getType() + "-" + parentId;
    }

    public String createChildId(
        final String parentId, final String childId, final EntityType childType) {
      return child.getType()
          + "-"
          + parentId
          + "-"
          + childType.name().toLowerCase()
          + "-"
          + childId;
    }
  }

  public enum IdentityJoinRelationshipType implements JoinRelationshipType {
    GROUP("group"),
    ROLE("role"),
    TENANT("tenant"),
    MEMBER("member");

    private final String type;

    IdentityJoinRelationshipType(final String type) {
      this.type = type;
    }

    @Override
    public String getType() {
      return type;
    }
  }
}
