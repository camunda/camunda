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
  private Long memberKey;

  private EntityJoinRelation join;

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

  public Long getMemberKey() {
    return memberKey;
  }

  public GroupEntity setMemberKey(final Long memberKey) {
    this.memberKey = memberKey;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public GroupEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }

  public static String getChildKey(final long groupKey, final long memberKey) {
    return String.format("%d-%d", groupKey, memberKey);
  }
}
