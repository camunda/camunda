/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.bpmn.random;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.test.util.bpmn.random.blocks.BlockSequenceBuilder.BlockSequenceBuilderFactory;
import io.camunda.zeebe.test.util.bpmn.random.blocks.ProcessBuilder;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/** Class to generate random processes and execution paths for those processes */
public final class RandomProcessGenerator {

  public static final int MAX_BLOCKS = 5;
  public static final int MAX_DEPTH = 3;
  public static final int MAX_BRANCHES = 3;

  public static final double PROBABILITY_BOUNDARY_TIMER_EVENT = 0.2;
  public static final double PROBABILITY_BOUNDARY_ERROR_EVENT = 0.2;

  private static final BlockSequenceBuilderFactory FACTORY = new BlockSequenceBuilderFactory();

  private final ProcessBuilder processBuilder;

  /**
   * Creates the random process generator
   *
   * @param seed seed for random noise generator
   * @param maxBlocks maximum number of blocks in a sequence (defaults to {@code 5})
   * @param maxDepth maximum level of depth for nested elements (defaults to {@code 3})
   * @param maxBranches maximum number of outgoing branches for a forking node (defaults to {@code
   *     3})
   */
  public RandomProcessGenerator(
      final long seed, final Integer maxBlocks, final Integer maxDepth, final Integer maxBranches) {
    final Random random = new Random(seed);

    final IDGenerator idGenerator = new IDGenerator(0);

    final ConstructionContext context =
        new ConstructionContext(
            random,
            idGenerator,
            FACTORY,
            Optional.ofNullable(maxBlocks).orElse(MAX_BLOCKS),
            Optional.ofNullable(maxDepth).orElse(MAX_DEPTH),
            Optional.ofNullable(maxBranches).orElse(MAX_BRANCHES),
            0);

    processBuilder = new ProcessBuilder(context);
  }

  /**
   * @return the build process and any potentially called child processes
   */
  public List<BpmnModelInstance> buildProcesses() {
    return processBuilder.buildProcess();
  }

  public ExecutionPath findRandomExecutionPath(final long seed) {
    return processBuilder.findRandomExecutionPath(new Random(seed));
  }

  // main method to test and debug this class
  public static void main(final String[] args) {
    final Random random = new Random();

    for (int i = 0; i < 10; i++) {
      final int index = i;
      System.out.println("Generating process " + index);

      final RandomProcessGenerator builder = new RandomProcessGenerator(random.nextLong(), 5, 3, 3);

      final var bpmnModelInstances = builder.buildProcesses();

      bpmnModelInstances.stream()
          .map(modelInstance -> new Tuple<>(createFile(modelInstance, index), modelInstance))
          .forEach(tuple -> Bpmn.writeModelToFile(tuple.getLeft(), tuple.getRight()));

      for (int p = 0; p < 5; p++) {
        final ExecutionPath path = builder.findRandomExecutionPath(random.nextLong());

        System.out.println("Execution path " + p + " :" + path);
      }
    }
  }

  private static File createFile(final BpmnModelInstance bpmnModelInstance, final int index) {
    return bpmnModelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
        .map(BaseElement::getId)
        .map(id -> index + "-" + id + ".bpmn")
        .map(File::new)
        .findFirst()
        .orElseThrow();
  }
}
