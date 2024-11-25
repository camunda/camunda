/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public class GroupEntity extends AbstractExporterEntity<GroupEntity> {
  private Long groupKey;
  private String name;
  private Long entityKey;
  private EntityJoin join;

  public Long getGroupKey() {
    return groupKey;
  }

  public GroupEntity setGroupKey(final Long groupKey) {
    this.groupKey = groupKey;
    return this;
  }

  public String getName() {
    return name;
  }

  public GroupEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public Long getEntityKey() {
    return entityKey;
  }

  public GroupEntity setEntityKey(final Long entityKey) {
    this.entityKey = entityKey;
    return this;
  }

  public EntityJoin<Relation> getJoin() {
    return join;
  }

  public GroupEntity setJoin(final EntityJoin<Relation> join) {
    this.join = join;
    return this;
  }

  public static String getMemberKey(final long groupKey, final long entityKey) {
    return String.format("%d-%d", groupKey, entityKey);
  }

  public enum Relation {
    GROUP,
    MEMBER
  }
}
