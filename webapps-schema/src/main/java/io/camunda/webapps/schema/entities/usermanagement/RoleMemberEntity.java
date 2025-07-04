/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.usermanagement;

import io.camunda.webapps.schema.entities.AbstractExporterEntity;
import io.camunda.zeebe.protocol.record.value.EntityType;

public class RoleMemberEntity extends AbstractExporterEntity<RoleMemberEntity> {
  private String memberId;
  private EntityType memberType;

  private EntityJoinRelation join;

  public String getMemberId() {
    return memberId;
  }

  public RoleMemberEntity setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public EntityType getMemberType() {
    return memberType;
  }

  public RoleMemberEntity setMemberType(final EntityType memberType) {
    this.memberType = memberType;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public RoleMemberEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
