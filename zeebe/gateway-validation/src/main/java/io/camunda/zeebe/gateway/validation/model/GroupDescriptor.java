/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.model;

public final class GroupDescriptor {
  private final String groupId;
  private final BranchDescriptor[] branches;

  public GroupDescriptor(final String groupId, final BranchDescriptor[] branches) {
    this.groupId = groupId;
    this.branches = branches;
  }

  public String groupId() {
    return groupId;
  }

  public BranchDescriptor[] branches() {
    return branches;
  }

  public int branchCount() {
    return branches.length;
  }
}
