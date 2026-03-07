/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

/** Shared entity for role/tenant/group membership tables. */
public class MembershipEntity {

  private String entityId;
  private String memberId;
  private String memberType;

  public String getEntityId() {
    return entityId;
  }

  public void setEntityId(final String entityId) {
    this.entityId = entityId;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(final String memberId) {
    this.memberId = memberId;
  }

  public String getMemberType() {
    return memberType;
  }

  public void setMemberType(final String memberType) {
    this.memberType = memberType;
  }
}
