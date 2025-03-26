/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.writer.ProcessInstanceWriter;
import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.SequenceFlowTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.VariableTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.SequenceFlowEntity;
import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceWriterIT extends OperateSearchAbstractIT {

  @Autowired private ProcessInstanceWriter processInstanceWriter;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependants;

  @Autowired private ListViewTemplate listViewTemplate;

  @Test
  public void shouldDeleteFinishedInstanceById() throws IOException {
    final Long processInstanceKey = 4503599627370497L;
    final ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.COMPLETED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*operate*");

    processInstanceWriter.deleteInstanceById(processInstance.getProcessInstanceKey());

    searchContainerManager.refreshIndices("*operate*");

    assertThrows(
        NotFoundException.class,
        () ->
            processInstanceReader.getProcessInstanceByKey(processInstance.getProcessInstanceKey()));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldDeleteCancelledInstanceById() throws IOException {
    final Long processInstanceKey = 4503599627370497L;
    final ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.CANCELED)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(FlowNodeInstanceTemplate.INDEX_NAME),
        new FlowNodeInstanceEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(SequenceFlowTemplate.INDEX_NAME),
        new SequenceFlowEntity().setProcessInstanceKey(processInstanceKey));
    testSearchRepository.createOrUpdateDocumentFromObject(
        getFullIndexNameForDependant(VariableTemplate.INDEX_NAME),
        new VariableEntity().setProcessInstanceKey(processInstanceKey));

    searchContainerManager.refreshIndices("*operate*");

    processInstanceWriter.deleteInstanceById(processInstanceKey);

    searchContainerManager.refreshIndices("*operate*");

    assertThrows(
        NotFoundException.class,
        () -> processInstanceReader.getProcessInstanceByKey(processInstanceKey));
    assertThatDependantsAreAlsoDeleted(processInstanceKey);
  }

  @Test
  public void shouldFailDeleteInstanceByIdWithInvalidState() throws IOException {
    final Long processInstanceKey = 4503599627370497L;
    final ProcessInstanceForListViewEntity processInstance =
        new ProcessInstanceForListViewEntity()
            .setId(String.valueOf(processInstanceKey))
            .setKey(processInstanceKey)
            .setProcessDefinitionKey(2251799813685248L)
            .setProcessInstanceKey(4503599627370497L)
            .setProcessName("Demo process parent")
            .setBpmnProcessId("demoProcess")
            .setState(ProcessInstanceState.ACTIVE)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setTreePath("PI_4503599627370497")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate*");

    assertThrows(
        IllegalArgumentException.class,
        () -> processInstanceWriter.deleteInstanceById(processInstanceKey));

    // Cleanup so as not to interfere with other tests
    processInstance.setState(ProcessInstanceState.COMPLETED);
    testSearchRepository.createOrUpdateDocumentFromObject(
        listViewTemplate.getFullQualifiedName(), processInstance.getId(), processInstance);

    searchContainerManager.refreshIndices("*operate*");
    processInstanceWriter.deleteInstanceById(processInstanceKey);
  }

  private void assertThatDependantsAreAlsoDeleted(final long finishedProcessInstanceKey)
      throws IOException {
    for (final ProcessInstanceDependant t : processInstanceDependants) {
      if (!(t instanceof OperationTemplate)) {
        final var index = t.getFullQualifiedName() + "*";
        final var field = ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
        final var response =
            testSearchRepository.searchTerm(
                index, field, finishedProcessInstanceKey, Object.class, 100);
        assertThat(response.size()).isZero();
      }
    }
  }

  private String getFullIndexNameForDependant(final String indexName) {
    final ProcessInstanceDependant dependant =
        processInstanceDependants.stream()
            .filter(template -> template.getFullQualifiedName().contains(indexName))
            .findAny()
            .orElse(null);

    return dependant.getFullQualifiedName();
  }
}
