/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.perf;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.queries.TaskByVariables;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.DatabaseTestExtension;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.descriptors.template.VariableTemplate;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.usertask.TaskEntity;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship;
import io.camunda.webapps.schema.entities.usertask.TaskJoinRelationship.TaskJoinRelationshipType;
import io.camunda.webapps.schema.entities.usertask.TaskProcessInstanceEntity;
import io.camunda.webapps.schema.entities.usertask.TaskState;
import io.camunda.zeebe.protocol.Protocol;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class TaskStorePerfIT extends TasklistIntegrationTest {
  private static final Logger LOG = LoggerFactory.getLogger(TaskStorePerfIT.class);

  @RegisterExtension @Autowired public DatabaseTestExtension databaseTestExtension;

  @Autowired private TaskStore taskStore;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  @Autowired private VariableTemplate variableIndex;

  @Autowired private TaskTemplate taskTemplate;

  @BeforeEach
  public void setUp() {
    super.before();
    databaseTestExtension.refreshTasklistIndices();
  }

  /**
   * This test depends on many factors like Elasticsearch configuration, hardware, current load,
   * etc. It is mainly meant to assert that the response time is within a certain order of
   * magnitude. In case of failure, please check if the expected response time should be increased.
   */
  @ParameterizedTest
  @MethodSource("performanceTestParams")
  void shouldQueryTasksByVariablesNotExceedResponseTime(
      final int numberOfVariablesPerTask,
      final int numberOfMatchingTasks,
      final int maxResponseTimeMs)
      throws Exception {
    final int total = 100_000;
    /**
     * The test creates 100_000 process instances, flownode instances, tasks and
     * (numberOfVariablesPerTask* 100_000) variables.
     */
    final List<TaskEntity> tasks = new ArrayList<>(total);
    final List<TaskProcessInstanceEntity> tasksPIs = new ArrayList<>(total);
    final List<FlowNodeInstanceEntity> flowNodeInstances = new ArrayList<>(total);
    final List<VariableEntity> variables = new ArrayList<>(total * numberOfVariablesPerTask);
    final long piOffset = total * 0;
    final long fniOffset = total * 1;
    final long taskOffset = total * 2;
    final long variableOffset = total * 3;
    for (int i = 0; i < total; i++) {
      final long fniKey = Protocol.encodePartitionId(1, fniOffset + i);
      final long processInstanceKey = Protocol.encodePartitionId(1, piOffset + i);
      final long taskKey = Protocol.encodePartitionId(1, taskOffset + i);
      flowNodeInstances.add(
          new FlowNodeInstanceEntity()
              .setId(String.valueOf(fniKey))
              .setKey(fniKey)
              .setProcessInstanceKey(processInstanceKey)
              .setType(FlowNodeType.USER_TASK)
              .setState(FlowNodeState.ACTIVE));
      for (int j = 0; j < numberOfVariablesPerTask; j++) {
        final long variableKey =
            Protocol.encodePartitionId(1, variableOffset + numberOfVariablesPerTask * i + j);
        variables.add(
            new VariableEntity()
                .setId(String.valueOf(variableKey))
                .setKey(variableKey)
                .setProcessInstanceKey(processInstanceKey)
                .setScopeKey(fniKey)
                .setName("var" + j)
                .setValue(
                    i < numberOfMatchingTasks ? String.format("\"value%d\"", j) : "\"other\""));
      }
      tasksPIs.add(
          new TaskProcessInstanceEntity()
              .setId(String.valueOf(processInstanceKey))
              .setProcessInstanceId(processInstanceKey)
              .setJoin(new TaskJoinRelationship(TaskJoinRelationshipType.PROCESS.getType())));
      tasks.add(
          new TaskEntity()
              .setId(String.valueOf(taskKey))
              .setKey(taskKey)
              .setProcessInstanceId(String.valueOf(processInstanceKey))
              .setFlowNodeInstanceId(String.valueOf(fniKey))
              .setState(TaskState.CREATED)
              .setJoin(
                  new TaskJoinRelationship(
                      TaskJoinRelationshipType.TASK.getType(), processInstanceKey)));
    }
    databaseTestExtension.bulkIndex(flowNodeInstanceIndex, flowNodeInstances);
    databaseTestExtension.bulkIndex(variableIndex, variables);
    databaseTestExtension.bulkIndex(taskTemplate, tasksPIs);
    databaseTestExtension.bulkIndex(taskTemplate, tasks, TaskEntity::getProcessInstanceId);

    LOG.info("Data loaded, starting performance test");
    assertWithRetry(
        3,
        () -> {
          final int pageSize = 10_000;
          final long start = System.currentTimeMillis();
          assertThat(
                  taskStore.getTasks(
                      new TaskQuery()
                          .setState(TaskState.CREATED)
                          .setPageSize(
                              pageSize) // use max allowed page size to make sure we get all tasks
                          .setTaskVariables(
                              IntStream.range(0, numberOfVariablesPerTask)
                                  .mapToObj(
                                      i ->
                                          new TaskByVariables()
                                              .setName("var" + i)
                                              .setValue(String.format("\"value%d\"", i)))
                                  .toArray(TaskByVariables[]::new))))
              .hasSize(Math.min(numberOfMatchingTasks, pageSize));
          LOG.info(
              "Performance test, for {} matches, finished in {} ms. Max response time is set to {} ms.",
              numberOfMatchingTasks,
              System.currentTimeMillis() - start,
              maxResponseTimeMs);
          assertThat(System.currentTimeMillis() - start).isLessThan(maxResponseTimeMs);
        });
  }

  /** (int numberOfVariablesPerTask, int numberOfMatchingTasks, int maxResponseTimeMs) */
  private static Stream<Arguments> performanceTestParams() {
    return Stream.of(
        Arguments.of(1, 1000, 500),
        Arguments.of(1, 3000, 1000),
        Arguments.of(2, 1000, 500),
        Arguments.of(2, 3000, 1000),
        Arguments.of(1, 10_000, 2000),
        Arguments.of(1, 30_000, 6000));
  }

  private void assertWithRetry(final int maxAttempts, final Runnable assertion)
      throws InterruptedException {
    int attempt = 0;
    while (attempt < maxAttempts) {
      try {
        assertion.run();
        return;
      } catch (final AssertionError e) {
        attempt++;
        if (attempt == maxAttempts) {
          throw e;
        }
        Thread.sleep(500);
      }
    }
  }
}
