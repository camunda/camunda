/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
      final int processes, final int pathsPerProcess) {
    final List<TestDataRecord> records = new ArrayList<>();

    for (int processIndex = 0; processIndex < processes; processIndex++) {
      final long processSeed = RANDOM.nextLong();

      final RandomProcessGenerator generator =
          new RandomProcessGenerator(processSeed, null, null, null);

      final List<BpmnModelInstance> bpmnModelInstances = generator.buildProcesses();

      final Set<ExecutionPath> paths = new HashSet<>();
      for (int pathIndex = 0; pathIndex < pathsPerProcess; pathIndex++) {
        final long pathSeed = RANDOM.nextLong();

        final ExecutionPath path = generator.findRandomExecutionPath(pathSeed);

        final boolean isDifferentPath = paths.add(path);

        if (isDifferentPath) {
          records.add(new TestDataRecord(processSeed, pathSeed, bpmnModelInstances, path));
        }
      }
    }

    return records;
  }

  public static TestDataRecord regenerateTestRecord(
      final long processSeed, final long executionPathSeed) {
    final RandomProcessGenerator generator =
        new RandomProcessGenerator(processSeed, null, null, null);

    final List<BpmnModelInstance> bpmnModelInstances = generator.buildProcesses();

    final ExecutionPath path = generator.findRandomExecutionPath(executionPathSeed);

    return new TestDataRecord(processSeed, executionPathSeed, bpmnModelInstances, path);
  }

  public static final class TestDataRecord {
    private final long processSeed;
    private final long executionPathSeed;

    private final List<BpmnModelInstance> bpmnModels;
    private final ExecutionPath executionPath;

    private TestDataRecord(
        final long processSeed,
        final long executionPathSeed,
        final List<BpmnModelInstance> bpmnModels,
        final ExecutionPath executionPath) {
      this.processSeed = processSeed;
      this.executionPathSeed = executionPathSeed;
      this.bpmnModels = bpmnModels;
      this.executionPath = executionPath;
    }

    public List<BpmnModelInstance> getBpmnModels() {
      return bpmnModels;
    }

    public ExecutionPath getExecutionPath() {
      return executionPath;
    }

    @Override
    public String toString() {
      return "TestDataRecord{"
          + "processSeed="
          + processSeed
          + ", executionPathSeed="
          + executionPathSeed
          + '}';
    }
  }
}
