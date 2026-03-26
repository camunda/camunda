/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.coordinator;

public class MemberMetadata {

  private final String memberId;
  private final String instanceId;
  private int memberEpoch;

  public MemberMetadata(final String memberId, final String instanceId) {
    this.memberId = memberId;
    memberEpoch = 0;
    this.instanceId = instanceId;
  }

  public String getMemberId() {
    return memberId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public int getMemberEpoch() {
    return memberEpoch;
  }

  public void incrementMemberEpoch() {
    memberEpoch++;
  }

  public boolean isStaticMember() {
    return instanceId != null && !instanceId.isBlank();
  }

  public static MemberMetadata dynamicMember(final String memberId) {
    return new MemberMetadata(memberId, null);
  }

  public static MemberMetadata staticMember(final String memberId, final String instanceId) {
    return new MemberMetadata(memberId, instanceId);
  }
}
