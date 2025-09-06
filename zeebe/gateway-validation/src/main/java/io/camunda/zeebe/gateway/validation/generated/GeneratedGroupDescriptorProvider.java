/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.validation.generated;

/*
 * GENERATED PLACEHOLDER (will be replaced by build-time domain spec codegen).
 * Provides a single dummy group so validator tests exercise positive path.
 */
import io.camunda.zeebe.gateway.validation.model.BranchDescriptor;
import io.camunda.zeebe.gateway.validation.model.GroupDescriptor;
import io.camunda.zeebe.gateway.validation.spi.GroupDescriptorProvider;

public final class GeneratedGroupDescriptorProvider implements GroupDescriptorProvider {

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
