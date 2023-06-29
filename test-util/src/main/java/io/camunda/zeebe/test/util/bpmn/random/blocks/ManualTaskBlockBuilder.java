/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random.blocks;

import io.camunda.zeebe.test.util.bpmn.random.BlockBuilder;
import io.camunda.zeebe.test.util.bpmn.random.BlockBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.ConstructionContext;

public class ManualTaskBlockBuilder extends AbstractBlockBuilder {

  public ManualTaskBlockBuilder(final ConstructionContext context) {
    super(context.getIdGenerator().nextId());
  }

  static class Factory implements BlockBuilderFactory {

    @Override
    public BlockBuilder createBlockBuilder(final ConstructionContext context) {
      return new ManualTaskBlockBuilder(context);
    }

    @Override
    public boolean isAddingDepth() {
      return false;
    }
  }
}
