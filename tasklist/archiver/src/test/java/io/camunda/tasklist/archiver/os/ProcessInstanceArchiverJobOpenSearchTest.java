/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.archiver.os;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.archiver.AbstractArchiverJob.ArchiveBatch;
import io.camunda.tasklist.archiver.ArchiverUtil;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for the orphan documents bug documented in GitHub issue #53786. Verifies that
 * the process-instance delete only fires after the dependents (variables, flow-nodes) succeed —
 * otherwise a partial failure would orphan child documents because the parent end-date row is gone
 * and the importer won't replay the records.
 */
@ExtendWith(MockitoExtension.class)
public class ProcessInstanceArchiverJobOpenSearchTest {

  private static final String VAR_INDEX = "tasklist-variable-index";
  private static final String FLOW_NODE_INDEX = "tasklist-flownode-instance-index";
  private static final String PROCESS_INSTANCE_INDEX = "tasklist-process-instance-index";
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  @InjectMocks
  private ProcessInstanceArchiverJobOpenSearch underTest =
      new ProcessInstanceArchiverJobOpenSearch(List.of(1));

  @Mock private ArchiverUtil archiverUtil;
  @Mock private VariableIndex variableIndex;
  @Mock private FlowNodeInstanceIndex flowNodeInstanceIndex;
  @Mock private ProcessInstanceIndex processInstanceIndex;

  @BeforeEach
  public void setUp() {
    lenient().when(variableIndex.getFullQualifiedName()).thenReturn(VAR_INDEX);
    lenient().when(flowNodeInstanceIndex.getFullQualifiedName()).thenReturn(FLOW_NODE_INDEX);
    lenient().when(processInstanceIndex.getFullQualifiedName()).thenReturn(PROCESS_INSTANCE_INDEX);
  }

  @Test
  public void archiveBatchSkipsProcessInstanceDeleteWhenVariablesDeleteFails() {
    final CompletableFuture<Long> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("OS variables delete failed"));
    when(archiverUtil.deleteDocuments(eq(VAR_INDEX), any(), any())).thenReturn(failed);
    when(archiverUtil.deleteDocuments(eq(FLOW_NODE_INDEX), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1L));

    final CompletableFuture<Map.Entry<String, Integer>> res =
        underTest.archiveBatch(new ArchiveBatch(List.of("pi-1")));

    assertThat(res).failsWithin(TIMEOUT);
    verify(archiverUtil, never()).deleteDocuments(eq(PROCESS_INSTANCE_INDEX), any(), any());
  }

  @Test
  public void archiveBatchSkipsProcessInstanceDeleteWhenFlowNodesDeleteFails() {
    final CompletableFuture<Long> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("OS flow-nodes delete failed"));
    when(archiverUtil.deleteDocuments(eq(VAR_INDEX), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1L));
    when(archiverUtil.deleteDocuments(eq(FLOW_NODE_INDEX), any(), any())).thenReturn(failed);

    final CompletableFuture<Map.Entry<String, Integer>> res =
        underTest.archiveBatch(new ArchiveBatch(List.of("pi-1")));

    assertThat(res).failsWithin(TIMEOUT);
    verify(archiverUtil, never()).deleteDocuments(eq(PROCESS_INSTANCE_INDEX), any(), any());
  }

  @Test
  public void archiveBatchDeletesDependentsBeforeParentOnSuccess() {
    when(archiverUtil.deleteDocuments(eq(VAR_INDEX), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(2L));
    when(archiverUtil.deleteDocuments(eq(FLOW_NODE_INDEX), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(3L));
    when(archiverUtil.deleteDocuments(eq(PROCESS_INSTANCE_INDEX), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(1L));

    final CompletableFuture<Map.Entry<String, Integer>> res =
        underTest.archiveBatch(new ArchiveBatch(List.of("pi-1")));

    assertThat(res).succeedsWithin(TIMEOUT).extracting(Map.Entry::getValue).isEqualTo(1);

    final InOrder varsThenParent = inOrder(archiverUtil);
    varsThenParent.verify(archiverUtil).deleteDocuments(eq(VAR_INDEX), any(), any());
    varsThenParent.verify(archiverUtil).deleteDocuments(eq(PROCESS_INSTANCE_INDEX), any(), any());
    final InOrder flowNodesThenParent = inOrder(archiverUtil);
    flowNodesThenParent.verify(archiverUtil).deleteDocuments(eq(FLOW_NODE_INDEX), any(), any());
    flowNodesThenParent
        .verify(archiverUtil)
        .deleteDocuments(eq(PROCESS_INSTANCE_INDEX), any(), any());
  }

  @Test
  public void archiveBatchReturnsNothingToArchiveWhenBatchIsNull() {
    final CompletableFuture<Map.Entry<String, Integer>> res = underTest.archiveBatch(null);

    assertThat(res).succeedsWithin(TIMEOUT).extracting(Map.Entry::getValue).isEqualTo(0);
    verify(archiverUtil, never()).deleteDocuments(any(), any(), any());
  }
}
