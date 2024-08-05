/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.queries.TaskQuery;
import io.camunda.tasklist.store.TaskStore;
import io.camunda.tasklist.util.TasklistZeebeIntegrationTest;
import io.camunda.tasklist.util.ZeebeTestUtil;
import io.camunda.tasklist.views.TaskSearchView;
import io.camunda.zeebe.protocol.Protocol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class ZeebeImportWithMissedDataIT extends TasklistZeebeIntegrationTest {

  private static final int IMPORTER_BATCH_SIZE = 2;

  @Autowired private TaskStore taskStore;

  @DynamicPropertySource
  protected static void registerProperties(final DynamicPropertyRegistry registry) {
    // make batch size smaller
    if (IS_ELASTIC) {
      registry.add(
          TasklistProperties.PREFIX + ".zeebeElasticsearch.batchSize", () -> IMPORTER_BATCH_SIZE);
    } else {
      registry.add(
          TasklistProperties.PREFIX + ".zeebeOpensearch.batchSize", () -> IMPORTER_BATCH_SIZE);
    }
  }

  @Test
  public void testTasksAreImported() throws Exception {
    // having
    final String processId = "demoProcess";
    final String flowNodeBpmnId = "utFlowNode";
    tester.createAndDeploySimpleProcess(processId, flowNodeBpmnId).waitUntil().processIsDeployed();
    final List<Long> processInstanceKeys =
        IntStream.range(0, 20)
            .mapToLong(
                i -> Long.valueOf(ZeebeTestUtil.startProcessInstance(zeebeClient, processId, null)))
            .boxed()
            .toList();

    // split by partitions
    final var keysByPartition =
        processInstanceKeys.stream()
            .collect(Collectors.groupingBy(k -> Protocol.decodePartitionId(k)));

    // wait for Zeebe to export data
    Thread.sleep(10000L);

    // split processInstanceKeys by batch size and remove "middle part" of Zeebe data
    final List<Long> keysForDeletion = new ArrayList<>();
    for (final var partition : keysByPartition.values()) {
      final AtomicInteger counter = new AtomicInteger();
      final Map<Integer, List<Long>> splittedKeys =
          partition.stream()
              .collect(Collectors.groupingBy(x -> counter.getAndIncrement() / IMPORTER_BATCH_SIZE));
      for (int i = 1; i < splittedKeys.size() - 1; i++) {
        keysForDeletion.addAll(splittedKeys.get(i));
      }
    }
    // delete all Zeebe records related to processInstanceKey=keysForDeletion
    databaseTestExtension.deleteByTermsQuery(
        zeebeExtension.getPrefix() + "*", "value.processInstanceKey", keysForDeletion, Long.class);

    // refresh Zeebe indices
    databaseTestExtension.refreshZeebeIndices();

    // trigger Tasklist importer
    tester
        .waitUntil()
        .tasksAreCreated(flowNodeBpmnId, processInstanceKeys.size() - keysForDeletion.size());

    // then all expected tasks are loaded - importer is not blocked
    final List<TaskSearchView> taskEntities =
        processInstanceKeys.stream()
            .map(String::valueOf)
            .flatMap(
                processInstanceKey ->
                    taskStore
                        .getTasks(
                            new TaskQuery()
                                .setProcessInstanceId(processInstanceKey)
                                .setPageSize(50))
                        .stream())
            .toList();

    assertThat(taskEntities).isNotEmpty();
    assertThat(
            taskEntities.stream()
                .map(TaskSearchView::getProcessInstanceId)
                .map(Long::valueOf)
                .collect(Collectors.toSet()))
        .isEqualTo(new HashSet<>(CollectionUtils.subtract(processInstanceKeys, keysForDeletion)));
  }
}
