/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;

public class RoleEntity extends AbstractExporterEntity<RoleEntity> {

  private Long key;
  private String name;
  private String memberId;
  private EntityJoinRelation<Long> join;

  public Long getKey() {
    return key;
  }

  public RoleEntity setKey(final Long key) {
    this.key = key;
    return this;
  }

  public String getName() {
    return name;
  }

  public RoleEntity setName(final String name) {
    this.name = name;
    return this;
  }

  public String getMemberId() {
    return memberId;
  }

  public RoleEntity setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public EntityJoinRelation<Long> getJoin() {
    return join;
  }

  public RoleEntity setJoin(final EntityJoinRelation<Long> join) {
    this.join = join;
    return this;
  }

  public static String getChildKey(final long roleKey, final String memberId) {
    return "%d-%s".formatted(roleKey, memberId);
  }
}
