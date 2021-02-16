/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.util.bpmn.random;

import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class TestDataGenerator {

  static final Random RANDOM = new Random();

  public static Collection<TestDataRecord> generateTestRecords(
      final int workflows, final int pathsPerWorkflow) {
    final List<TestDataRecord> records = new ArrayList<>();

    for (int workflowIndex = 0; workflowIndex < workflows; workflowIndex++) {
      final long workflowSeed = RANDOM.nextLong();

      final RandomWorkflowGenerator generator =
          new RandomWorkflowGenerator(workflowSeed, null, null, null);

      final BpmnModelInstance bpmnModelInstance = generator.buildWorkflow();

      final Set<ExecutionPath> paths = new HashSet<>();
      for (int pathIndex = 0; pathIndex < pathsPerWorkflow; pathIndex++) {
        final long pathSeed = RANDOM.nextLong();

        final ExecutionPath path = generator.findRandomExecutionPath(pathSeed);

        final boolean isDifferentPath = paths.add(path);

        if (isDifferentPath) {
          records.add(new TestDataRecord(workflowSeed, pathSeed, bpmnModelInstance, path));
        }
      }
    }

    return records;
  }

  public static TestDataRecord regenerateTestRecord(
      final long workflowSeed, final long executionPathSeed) {
    final RandomWorkflowGenerator generator =
        new RandomWorkflowGenerator(workflowSeed, null, null, null);

    final BpmnModelInstance bpmnModelInstance = generator.buildWorkflow();

    final ExecutionPath path = generator.findRandomExecutionPath(executionPathSeed);

    return new TestDataRecord(workflowSeed, executionPathSeed, bpmnModelInstance, path);
  }

  public static final class TestDataRecord {
    private final long workFlowSeed;
    private final long executionPathSeed;

    private final BpmnModelInstance bpmnModel;
    private final ExecutionPath executionPath;

    private TestDataRecord(
        final long workFlowSeed,
        final long executionPathSeed,
        final BpmnModelInstance bpmnModel,
        final ExecutionPath executionPath) {
      this.workFlowSeed = workFlowSeed;
      this.executionPathSeed = executionPathSeed;
      this.bpmnModel = bpmnModel;
      this.executionPath = executionPath;
    }

    public BpmnModelInstance getBpmnModel() {
      return bpmnModel;
    }

    public ExecutionPath getExecutionPath() {
      return executionPath;
    }

    @Override
    public String toString() {
      return "TestDataRecord{"
          + "workFlowSeed="
          + workFlowSeed
          + ", executionPathSeed="
          + executionPathSeed
          + '}';
    }
  }
}
