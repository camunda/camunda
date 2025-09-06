/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.generated;

import io.camunda.zeebe.gateway.validation.model.BranchDescriptor;
import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import io.camunda.zeebe.gateway.validation.spi.GroupDescriptorProvider;

/** Temporary hard-coded provider used until build-time generation is implemented. */
public final class FakeGroupDescriptorProvider implements GroupDescriptorProvider {

  private static final GroupDescriptor DUMMY =
      new GroupDescriptor("DummyGroup", new BranchDescriptor[] {new BranchDescriptor(0, 0)});

  @Override
  public GroupDescriptor find(final String groupId) {
    if ("DummyGroup".equals(groupId)) {
      return DUMMY;
    }
    return null;
  }
}
