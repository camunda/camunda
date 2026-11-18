/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessInstanceRetentionBatchTest {

  private static final String INDEX_PREFIX = "test";

  @Test
  void shouldMapKeysToListViewFields() {
    // given
    final var batch = new TestBatch(List.of(1L, 2L), List.of(3L));

    // when
    final var idsByField = batch.createIdsByFieldMap(new ListViewTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ListViewTemplate.PROCESS_INSTANCE_KEY,
                List.of("1", "2"),
                ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY,
                List.of("3")));
  }

  @Test
  void shouldMapKeysToProcessInstanceDependantFields() {
    // given
    final var batch = new TestBatch(List.of(1L), List.of(2L, 3L));

    // when
    final var idsByField =
        batch.createIdsByFieldMap(new FlowNodeInstanceTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                List.of("1"),
                ProcessInstanceDependant.ROOT_PROCESS_INSTANCE_KEY,
                List.of("2", "3")));
  }

  @Test
  void shouldUseFieldNamesDefinedByTemplate() {
    // given
    final var batch = new TestBatch(List.of(1L), List.of(2L));

    // when
    final var idsByField = batch.createIdsByFieldMap(new TaskTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                TaskTemplate.PROCESS_INSTANCE_ID,
                List.of("1"),
                ProcessInstanceDependant.ROOT_PROCESS_INSTANCE_KEY,
                List.of("2")));
  }

  @Test
  void shouldOmitProcessInstanceKeyFieldWhenNoProcessInstanceKeys() {
    // given
    final var batch = new TestBatch(List.of(), List.of(1L));

    // when
    final var idsByField =
        batch.createIdsByFieldMap(new FlowNodeInstanceTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField)
        .containsExactlyEntriesOf(
            Map.of(ProcessInstanceDependant.ROOT_PROCESS_INSTANCE_KEY, List.of("1")));
  }

  @Test
  void shouldOmitRootProcessInstanceKeyFieldWhenNoRootProcessInstanceKeys() {
    // given
    final var batch = new TestBatch(List.of(1L), List.of());

    // when
    final var idsByField =
        batch.createIdsByFieldMap(new FlowNodeInstanceTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField)
        .containsExactlyEntriesOf(
            Map.of(ProcessInstanceDependant.PROCESS_INSTANCE_KEY, List.of("1")));
  }

  @Test
  void shouldReturnEmptyMapForEmptyBatch() {
    // given
    final var batch = new TestBatch(List.of(), List.of());

    // when
    final var idsByField = batch.createIdsByFieldMap(new ListViewTemplate(INDEX_PREFIX, true));

    // then
    assertThat(idsByField).isEmpty();
  }

  @Test
  void shouldRejectTemplateNotRelatedToProcessInstances() {
    // given
    final var batch = new TestBatch(List.of(1L), List.of(2L));
    final var template = new BatchOperationTemplate(INDEX_PREFIX, true);

    // when / then
    assertThatThrownBy(() -> batch.createIdsByFieldMap(template))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BatchOperationTemplate.class.getName());
  }

  private record TestBatch(List<Long> processInstanceKeys, List<Long> rootProcessInstanceKeys)
      implements ProcessInstanceRetentionBatch {}
}
