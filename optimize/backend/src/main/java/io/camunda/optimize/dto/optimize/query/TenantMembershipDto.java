/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

/** POC: persisted assignment of a user, group or client to a logical tenant. */
public class TenantMembershipDto {

  public enum MemberType {
    USER,
    GROUP,
    CLIENT,
    ROLE,
    MAPPING_RULE
  }

  private String id;
  private String tenantId;
  private MemberType memberType;
  private String memberId;

  public TenantMembershipDto() {}

  public TenantMembershipDto(
      final String tenantId, final MemberType memberType, final String memberId) {
    id = idOf(tenantId, memberType, memberId);
    this.tenantId = tenantId;
    this.memberType = memberType;
    this.memberId = memberId;
  }

  public static String idOf(
      final String tenantId, final MemberType memberType, final String memberId) {
    return tenantId + ":" + memberType + ":" + memberId;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(final String tenantId) {
    this.tenantId = tenantId;
  }

  public MemberType getMemberType() {
    return memberType;
  }

  public void setMemberType(final MemberType memberType) {
    this.memberType = memberType;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(final String memberId) {
    this.memberId = memberId;
  }
}
