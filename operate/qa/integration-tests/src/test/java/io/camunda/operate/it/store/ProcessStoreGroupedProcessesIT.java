/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.entities.ProcessEntity;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessStoreGroupedProcessesIT extends OperateSearchAbstractIT {

  private static final String BPMN_PROCESS_ID = "processWithManyVersions";
  private static final int TOTAL_VERSIONS = 120;

  @Autowired private ProcessIndex processDefinitionIndex;

  @Autowired private ProcessStore processStore;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    for (int version = 1; version <= TOTAL_VERSIONS; version++) {
      final long key = 9000000000000000L + version;
      final ProcessEntity processEntity =
          new ProcessEntity()
              .setKey(key)
              .setId(String.valueOf(key))
              .setBpmnProcessId(BPMN_PROCESS_ID)
              .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
              .setName("Process with many versions")
              .setVersion(version);
      testSearchRepository.createOrUpdateDocumentFromObject(
          processDefinitionIndex.getFullQualifiedName(), processEntity.getId(), processEntity);
    }
    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void shouldReturnAllVersionsSortedDescending() {
    // given
    final ProcessStore.ProcessKey processKey =
        new ProcessStore.ProcessKey(BPMN_PROCESS_ID, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // when
    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> results =
        processStore.getProcessesGrouped(
            TenantOwned.DEFAULT_TENANT_IDENTIFIER, Set.of(BPMN_PROCESS_ID));

    // then
    final List<ProcessEntity> versions = results.get(processKey);
    assertThat(versions).hasSize(TOTAL_VERSIONS);
    assertThat(versions.getFirst().getVersion()).isEqualTo(TOTAL_VERSIONS);
    assertThat(versions.getLast().getVersion()).isEqualTo(1);

    final Set<Integer> returnedVersions =
        versions.stream()
            .map(ProcessEntity::getVersion)
            .collect(java.util.stream.Collectors.toSet());
    for (int i = 1; i <= TOTAL_VERSIONS; i++) {
      assertThat(returnedVersions).contains(i);
    }
  }
}
