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

public class TenantMemberEntity extends AbstractExporterEntity<TenantMemberEntity> {

  private String memberId;
  private EntityType memberType;

  private EntityJoinRelation<String> join;

  public String getMemberId() {
    return memberId;
  }

  public TenantMemberEntity setMemberId(final String memberId) {
    this.memberId = memberId;
    return this;
  }

  public EntityType getMemberType() {
    return memberType;
  }

  public TenantMemberEntity setMemberType(final EntityType memberType) {
    this.memberType = memberType;
    return this;
  }

  public EntityJoinRelation<String> getJoin() {
    return join;
  }

  public TenantMemberEntity setJoin(final EntityJoinRelation<String> join) {
    this.join = join;
    return this;
  }

  public static String getChildKey(final String tenantId, final String memberId) {
    return String.format("%s-%s", tenantId, memberId);
  }
}
