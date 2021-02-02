/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random;

/**
 * Implementations of this interface create a block builder
 *
 * <p>Implementations of this interface need to be registered in {@code
 * BlockSequenceBuilder#BLOCK_BUILDER_FACTORIES}
 */
public interface BlockBuilderFactory {

  BlockBuilder createBlockBuilder(ConstructionContext context);

  /**
   * Returns {@code true} if the block builder is adding depth (nested elements) to the workflow
   * that will be generated
   *
   * @return {@code true} if the block builder is adding depth (nested elements) to the workflow *
   *     that will be generated
   */
  boolean isAddingDepth();
}
