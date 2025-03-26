/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.entities.listview.ListViewJoinRelation;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ProcessInstanceReaderIT extends OperateSearchAbstractIT {

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private OperationTemplate operationTemplate;

  @Autowired private ProcessInstanceReader processInstanceReader;

  @Autowired private UserService userService;

  private ProcessInstanceForListViewEntity processInstanceData;
  private OperationEntity operationData;

  @Override
  protected void runAdditionalBeforeAllSetup() throws Exception {
    final Long processInstanceKey = 2251799813685251L;
    final String indexName = listViewTemplate.getFullQualifiedName();

    processInstanceData =
        new ProcessInstanceForListViewEntity()
            .setId("2251799813685251")
            .setKey(processInstanceKey)
            .setPartitionId(1)
            .setProcessDefinitionKey(2251799813685249L)
            .setProcessName("Demo process")
            .setProcessVersion(1)
            .setBpmnProcessId("demoProcess")
            .setStartDate(OffsetDateTime.now())
            .setState(ProcessInstanceState.ACTIVE)
            .setTreePath("PI_2251799813685251")
            .setIncident(true)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .setProcessInstanceKey(processInstanceKey)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName, String.valueOf(processInstanceKey), processInstanceData);

    operationData = new OperationEntity();
    operationData.setProcessInstanceKey(processInstanceData.getProcessInstanceKey());
    operationData.setUsername(userService.getCurrentUser().getUsername());
    operationData.setState(OperationState.SCHEDULED);

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operationData);

    searchContainerManager.refreshIndices("*operate*");
  }

  @Test
  public void testGetProcessInstanceWithOperationsByKeyWithCorrectKey() {
    // When
    final ListViewProcessInstanceDto processInstance =
        processInstanceReader.getProcessInstanceWithOperationsByKey(
            processInstanceData.getProcessInstanceKey());
    assertThat(processInstance.getId())
        .isEqualTo(String.valueOf(processInstanceData.getProcessInstanceKey()));
    assertThat(processInstance.getOperations().size()).isEqualTo(1);
    assertThat(processInstance.getOperations().get(0).getId()).isEqualTo(operationData.getId());
  }

  @Test
  public void testGetProcessInstanceWithCorrectKey() {
    // When
    final ProcessInstanceForListViewEntity processInstance =
        processInstanceReader.getProcessInstanceByKey(processInstanceData.getProcessInstanceKey());
    assertThat(processInstance.getId())
        .isEqualTo(String.valueOf(processInstanceData.getProcessInstanceKey()));
  }

  @Test
  public void testGetProcessInstanceWithInvalidKey() {
    assertThrows(NotFoundException.class, () -> processInstanceReader.getProcessInstanceByKey(1L));
  }

  @Test
  public void testGetProcessInstanceWithOperationsWithInvalidKey() {
    assertThrows(
        NotFoundException.class,
        () -> processInstanceReader.getProcessInstanceWithOperationsByKey(1L));
  }
}
