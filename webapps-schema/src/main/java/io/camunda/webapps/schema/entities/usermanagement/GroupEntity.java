/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.webapps.schema.entities.BeforeVersion880;

public class GroupEntity extends AbstractExporterEntity<GroupEntity> {

  @BeforeVersion880 private Long key;
  @BeforeVersion880 private String groupId;
  @BeforeVersion880 private String name;
  @BeforeVersion880 private String description;

  @BeforeVersion880 private EntityJoinRelation join;

  public Long getKey() {
    return key;
  }

  public GroupEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getGroupId() {
    return groupId;
  }

  public GroupEntity setGroupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  public String getName() {
    return name;
  }

  public GroupEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public GroupEntity setDescription(final String description) {
    this.description = description;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public GroupEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
