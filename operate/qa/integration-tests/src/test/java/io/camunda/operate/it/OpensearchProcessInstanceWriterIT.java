/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Time;
import org.springframework.beans.factory.annotation.Autowired;

public class OpensearchProcessInstanceWriterIT extends OperateSearchAbstractIT {

  private static final Time DEFAULT_REFRESH_INTERVAL = Time.of(t -> t.time("1s"));
  private static final Time REFRESH_DISABLED = Time.of(t -> t.time("-1"));

  @Autowired(required = false)
  private OpenSearchClient operateOpenSearchClient;

  @Autowired private ProcessInstanceWriter processInstanceWriter;
  @Autowired private ProcessInstanceReader processInstanceReader;
  @Autowired private ListViewTemplate listViewTemplate;
  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  private Time originalRefreshInterval;
  private boolean refreshDisabled;

  @BeforeEach
  void assumeOpensearch() {
    assumeThat(DatabaseInfo.isOpensearch()).isTrue();
  }

  @AfterEach
  void restoreRefreshInterval() throws IOException {
    if (refreshDisabled) {
      setFlowNodeRefreshInterval(
          originalRefreshInterval != null ? originalRefreshInterval : DEFAULT_REFRESH_INTERVAL);
      refreshDisabled = false;
    }
  }

  @Test
  void shouldDeleteInstanceWhenDependantDocumentsWereRemovedAfterTheLastRefresh()
      throws IOException {
    // given
    final long processInstanceKey = ThreadLocalRandom.current().nextLong(1L << 52, 1L << 53);
    final var flowNodeIndex = flowNodeInstanceTemplate.getFullQualifiedName();
    createFinishedProcessInstance(processInstanceKey);
    final var flowNodeIds =
        List.of(
            createFlowNodeInstance(flowNodeIndex, processInstanceKey),
            createFlowNodeInstance(flowNodeIndex, processInstanceKey));
    searchContainerManager.refreshIndices("*operate*");

    originalRefreshInterval = currentFlowNodeRefreshInterval();
    setFlowNodeRefreshInterval(REFRESH_DISABLED);
    refreshDisabled = true;
    for (final var id : flowNodeIds) {
      operateOpenSearchClient.delete(d -> d.index(flowNodeIndex).id(id));
    }

    for (final var id : flowNodeIds) {
      assertThat(operateOpenSearchClient.exists(e -> e.index(flowNodeIndex).id(id)).value())
          .isFalse();
    }
    assertThat(
            testSearchRepository.searchTerm(
                flowNodeIndex,
                ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                processInstanceKey,
                Object.class,
                10))
        .hasSameSizeAs(flowNodeIds);

    // when
    processInstanceWriter.deleteInstanceById(processInstanceKey);

    // then
    searchContainerManager.refreshIndices("*operate*");
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> processInstanceReader.getProcessInstanceByKey(processInstanceKey));
  }

  private void createFinishedProcessInstance(final long processInstanceKey) throws IOException {
    final var processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(processInstanceKey)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.COMPLETED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_" + processInstanceKey)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);
  }

  private String createFlowNodeInstance(final String index, final long processInstanceKey)
      throws IOException {
    return testSearchRepository.createOrUpdateDocumentFromObject(
        index, new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
  }

  private Time currentFlowNodeRefreshInterval() throws IOException {
    final var index = flowNodeInstanceTemplate.getFullQualifiedName();
    final var settings =
        operateOpenSearchClient.indices().getSettings(g -> g.index(index)).result().get(index);
    return settings.settings().index().refreshInterval();
  }

  private void setFlowNodeRefreshInterval(final Time refreshInterval) throws IOException {
    final var index = flowNodeInstanceTemplate.getFullQualifiedName();
    operateOpenSearchClient
        .indices()
        .putSettings(p -> p.index(index).settings(s -> s.refreshInterval(refreshInterval)));
  }
}
