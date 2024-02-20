/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.it;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.operate.entities.OperationEntity;
import io.camunda.operate.entities.OperationState;
import io.camunda.operate.entities.listview.ListViewJoinRelation;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.OperationTemplate;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.util.j5templates.OperateSearchAbstractIT;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.security.UserService;
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
    Long processInstanceKey = 2251799813685251L;
    String indexName = listViewTemplate.getFullQualifiedName();

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
            .setTenantId(DEFAULT_TENANT_ID)
            .setProcessInstanceKey(processInstanceKey)
            .setJoinRelation(new ListViewJoinRelation("processInstance"));

    testSearchRepository.createOrUpdateDocumentFromObject(
        indexName, String.valueOf(processInstanceKey), processInstanceData);

    operationData = new OperationEntity();
    operationData.setId("operation-1");
    operationData.setProcessInstanceKey(processInstanceData.getProcessInstanceKey());
    operationData.setUsername(userService.getCurrentUser().getUsername());
    operationData.setState(OperationState.SCHEDULED);

    testSearchRepository.createOrUpdateDocumentFromObject(
        operationTemplate.getFullQualifiedName(), operationData);

    searchContainerManager.refreshIndices("*");
  }

  @Test
  public void testGetProcessInstanceWithOperationsByKeyWithCorrectKey() {
    // When
    ListViewProcessInstanceDto processInstance =
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
    ProcessInstanceForListViewEntity processInstance =
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
