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
import io.camunda.zeebe.protocol.record.value.EntityType;

public class GroupMemberEntity extends AbstractExporterEntity<GroupMemberEntity> {

  @BeforeVersion880 private String memberId;
  @BeforeVersion880 private EntityType memberType;

  @BeforeVersion880 private EntityJoinRelation join;

  public String getMemberId() {
    return memberId;
  }

  public GroupMemberEntity setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public EntityType getMemberType() {
    return memberType;
  }

  public GroupMemberEntity setMemberType(final EntityType memberType) {
    this.memberType = memberType;
    return this;
  }

  public EntityJoinRelation getJoin() {
    return join;
  }

  public GroupMemberEntity setJoin(final EntityJoinRelation join) {
    this.join = join;
    return this;
  }
}
