/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it.store;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.store.ProcessStore;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessStoreIT extends OperateSearchAbstractIT {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private ProcessIndex processDefinitionIndex;

  @Autowired private ProcessStore processStore;

  private ProcessEntity firstProcessDefinition;
  private ProcessEntity secondProcessDefinition;
  private ProcessEntity thirdProcessDefinition;
  private ProcessInstanceForListViewEntity firstProcessInstance;
  private ProcessInstanceForListViewEntity secondProcessInstance;
  private ProcessInstanceForListViewEntity thirdProcessInstance;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final String resourceXml =
        testResourceManager.readResourceFileContentsAsString("demoProcess_v_1.bpmn");
    firstProcessDefinition =
        new ProcessEntity()
            .setKey(2251799813685248L)
            .setId("2251799813685248")
            .setBpmnProcessId("demoProcess")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setName("Demo process")
            .setBpmnXml(resourceXml);

    secondProcessDefinition =
        new ProcessEntity()
            .setKey(2251799813685249L)
            .setId("2251799813685249")
            .setBpmnProcessId("demoProcess-1")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setName("Demo process 1")
            .setBpmnXml(resourceXml);

    thirdProcessDefinition =
        new ProcessEntity()
            .setKey(3251799813685249L)
            .setId("3251799813685249")
            .setBpmnProcessId("demoProcess-2")
            .setTenantId("tenant2")
            .setName("Demo process 2")
            .setBpmnXml(resourceXml);

    firstProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("4503599627370497")
            .setKey(4503599627370497L)
            .setProcessDefinitionKey(firstProcessDefinition.getKey())
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_4503599627370497")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    secondProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(2251799813685251L)
            .setProcessDefinitionKey(secondProcessDefinition.getKey())
            .setProcessInstanceKey(2251799813685251L)
            .setParentProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process child 1")
            .setBpmnProcessId("demoProcess-1")
            .setState(ProcessInstanceState.COMPLETED)
            .setTreePath("PI_2251799813685251")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setIncident(false)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    thirdProcessInstance =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685252")
            .setKey(2251799813685252L)
            .setProcessDefinitionKey(secondProcessDefinition.getKey())
            .setProcessInstanceKey(2251799813685252L)
            .setParentProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process child 2")
            .setBpmnProcessId("demoProcess-1")
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685252")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setIncident(true)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    final var definitionEntities =
        List.of(firstProcessDefinition, secondProcessDefinition, thirdProcessDefinition);
    for (final var entity : definitionEntities) {
      testSearchRepository.createOrUpdateDocumentFromObject(
          processDefinitionIndex.getFullQualifiedName(), entity.getId(), entity);
    }
    final var instanceEntities =
        List.of(firstProcessInstance, secondProcessInstance, thirdProcessInstance);
    for (final var entity : instanceEntities) {
      testSearchRepository.createOrUpdateDocumentFromObject(
          listViewTemplate.getFullQualifiedName(), entity.getId(), entity);
    }

    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void testGetDistinctCountFor() {
    final Optional<Long> optionalCount =
        processStore.getDistinctCountFor(ListViewTemplate.BPMN_PROCESS_ID);

    assertThat(optionalCount).isNotNull();
    assertThat(optionalCount.get()).isEqualTo(3L);
  }

  @Test
  public void testGetProcessByKey() {
    final ProcessEntity result = processStore.getProcessByKey(secondProcessDefinition.getKey());

    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(secondProcessDefinition.getBpmnProcessId());
    assertThat(result.getKey()).isEqualTo(secondProcessDefinition.getKey());
  }

  @Test
  public void testGetDiagramByKey() {
    final String xml = processStore.getDiagramByKey(secondProcessDefinition.getKey());
    assertThat(xml)
        .isEqualTo(testResourceManager.readResourceFileContentsAsString("demoProcess_v_1.bpmn"));
  }

  @Test
  public void testGetProcessesGrouped() {
    final Map<ProcessStore.ProcessKey, List<ProcessEntity>> results =
        processStore.getProcessesGrouped(
            TenantOwned.DEFAULT_TENANT_IDENTIFIER,
            Set.of(
                firstProcessDefinition.getBpmnProcessId(),
                secondProcessDefinition.getBpmnProcessId(),
                thirdProcessDefinition.getBpmnProcessId()));

    assertThat(results.values().size()).isEqualTo(2);
    assertThat(
            results
                .get(
                    new ProcessStore.ProcessKey(
                        firstProcessDefinition.getBpmnProcessId(),
                        TenantOwned.DEFAULT_TENANT_IDENTIFIER))
                .size())
        .isEqualTo(1);
    assertThat(
            results
                .get(
                    new ProcessStore.ProcessKey(
                        secondProcessDefinition.getBpmnProcessId(),
                        TenantOwned.DEFAULT_TENANT_IDENTIFIER))
                .size())
        .isEqualTo(1);
  }

  @Test
  public void testGetProcessesIdsToProcessesWithFields() {
    final Map<Long, ProcessEntity> results =
        processStore.getProcessesIdsToProcessesWithFields(
            Set.of(
                firstProcessDefinition.getBpmnProcessId(),
                secondProcessDefinition.getBpmnProcessId()),
            10,
            "name",
            "bpmnProcessId",
            "key");
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get(firstProcessDefinition.getKey()).getBpmnProcessId())
        .isEqualTo(firstProcessDefinition.getBpmnProcessId());
    assertThat(results.get(secondProcessDefinition.getKey()).getBpmnProcessId())
        .isEqualTo(secondProcessDefinition.getBpmnProcessId());
  }

  @Test
  public void testGetProcessInstanceListViewByKey() {
    final ProcessInstanceForListViewEntity result =
        processStore.getProcessInstanceListViewByKey(thirdProcessInstance.getProcessInstanceKey());

    assertThat(result).isNotNull();
    assertThat(result.getBpmnProcessId()).isEqualTo(thirdProcessInstance.getBpmnProcessId());
    assertThat(result.getKey()).isEqualTo(thirdProcessInstance.getProcessInstanceKey());
  }

  @Test
  public void testGetCoreStatistics() {
    final Map<String, Long> results = processStore.getCoreStatistics(Set.of("demoProcess-1"));
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get("running")).isEqualTo(1);
    assertThat(results.get("incidents")).isEqualTo(1);
  }

  @Test
  public void testGetProcessInstanceTreePathById() {
    final String result = processStore.getProcessInstanceTreePathById(thirdProcessInstance.getId());
    assertThat(result).isEqualTo(thirdProcessInstance.getTreePath());
  }

  @Test
  public void testDeleteDocument() throws IOException {
    final Long processKey = 2951799813685251L;
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        new ProcessInstanceForListViewEntity()
            .setKey(processKey)
            .setId(String.valueOf(processKey))
            .setBpmnProcessId("fakeProcess")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    searchContainerManager.refreshIndices("*operate-list*");

    final long deleted =
        processStore.deleteDocument(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.KEY,
            String.valueOf(processKey));

    searchContainerManager.refreshIndices("*operate-list*");

    final var response =
        testSearchRepository.searchTerm(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            processKey,
            Object.class,
            1);
    assertThat(response.size()).isZero();
    assertThat(deleted).isEqualTo(1L);
  }

  @Test
  public void testGetProcessInstancesByProcessAndStates() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByProcessAndStates(
            secondProcessDefinition.getKey(), Set.of(ProcessInstanceState.COMPLETED), 10, null);

    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getProcessDefinitionKey())
        .isEqualTo(secondProcessDefinition.getKey());
    assertThat(results.get(0).getState()).isEqualTo(ProcessInstanceState.COMPLETED);
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithMultipleStates() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByProcessAndStates(
            secondProcessDefinition.getKey(),
            Set.of(ProcessInstanceState.COMPLETED, ProcessInstanceState.ACTIVE),
            10,
            null);

    assertThat(results.size()).isEqualTo(2);
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessDefinitionKey().equals(secondProcessDefinition.getKey())
                            && data.getState().equals(ProcessInstanceState.COMPLETED))
                .findAny()
                .orElse(null))
        .isNotNull();
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessDefinitionKey().equals(secondProcessDefinition.getKey())
                            && data.getState().equals(ProcessInstanceState.ACTIVE))
                .findAny()
                .orElse(null))
        .isNotNull();
  }

  @Test
  public void testGetProcessInstancesByParentKeys() {
    final List<ProcessInstanceForListViewEntity> results =
        processStore.getProcessInstancesByParentKeys(
            Set.of(secondProcessInstance.getParentProcessInstanceKey()), 10, null);

    assertThat(results.size()).isEqualTo(2);
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessInstanceKey()
                                .equals(secondProcessInstance.getProcessInstanceKey())
                            && data.getParentProcessInstanceKey()
                                .equals(secondProcessInstance.getParentProcessInstanceKey()))
                .findAny()
                .orElse(null))
        .isNotNull();
    assertThat(
            results.stream()
                .filter(
                    data ->
                        data.getProcessInstanceKey()
                                .equals(thirdProcessInstance.getProcessInstanceKey())
                            && data.getParentProcessInstanceKey()
                                .equals(thirdProcessInstance.getParentProcessInstanceKey()))
                .findAny()
                .orElse(null))
        .isNotNull();
  }

  @Test
  public void testDeleteProcessDefinitionsByKeys() throws IOException {
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        new ProcessEntity()
            .setKey(2251799813685298L)
            .setBpmnProcessId("testProcess1")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setName("Test process 1"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        processDefinitionIndex.getFullQualifiedName(),
        new ProcessEntity()
            .setKey(2251799813685299L)
            .setBpmnProcessId("testProcess2")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setName("Test process 2"));
    searchContainerManager.refreshIndices("*operate-process*");

    final long deleted =
        processStore.deleteProcessDefinitionsByKeys(2251799813685298L, 2251799813685299L);
    assertThat(deleted).isEqualTo(2);

    searchContainerManager.refreshIndices("*operate-process*");
  }

  @Test
  public void testDeleteProcessInstancesAndDependants() throws IOException {
    final Long processKey = 2951799813685251L;
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(),
        new ProcessInstanceForListViewEntity()
            .setKey(processKey)
            .setId(String.valueOf(processKey))
            .setBpmnProcessId("fakeProcess")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processKey));

    searchContainerManager.refreshIndices("*operate*");

    final long deleted = processStore.deleteProcessInstancesAndDependants(Set.of(processKey));

    searchContainerManager.refreshIndices("*operate*");
    final var response =
        testSearchRepository.searchTerm(
            listViewTemplate.getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            processKey,
            Object.class,
            1);
    assertThat(response.size()).isZero();
    assertThat(deleted).isEqualTo(4);
  }

  private String getFullIndexNameForDependant(final String indexName) {
    final ProcessInstanceDependant dependant =
        processInstanceDependantTemplates.stream()
            .filter(template -> template.getFullQualifiedName().contains(indexName))
            .findAny()
            .orElse(null);

    return dependant.getFullQualifiedName();
  }
}
