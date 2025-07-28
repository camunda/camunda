/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.web.WebAppConfiguration;

@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfiguration.class, UnifiedConfigurationHelper.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@WithMockUser(username = AuthorizationIT.USER)
public class AuthorizationIT {
  protected static final String USER = "calculon";
  @MockBean private ProcessInstanceReader processInstanceReader;
  @MockBean private PermissionsService permissionsService;
  @MockBean private BatchOperationWriter batchOperationWriter;
  @Autowired private ProcessInstanceRestService processInstanceRestService;

  @Test
  public void testNoWritePermissionsForSingleOperation() {
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    final var processId = "23";
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(processId));
    when(permissionsService.hasPermissionForProcess(
            processId, PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(false);

    assertThatExceptionOfType(NotAuthorizedException.class)
        .isThrownBy(
            () ->
                processInstanceRestService.operation(
                    "23",
                    new CreateOperationRequestDto()
                        .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)));
    verifyNoInteractions(batchOperationWriter);
  }

  @Test
  public void testWritePermissionsForBatchOperation() {
    when(batchOperationWriter.scheduleBatchOperation(any())).thenReturn(new BatchOperationEntity());

    final BatchOperationEntity batchOperationEntity =
        processInstanceRestService.createBatchOperation(
            new CreateBatchOperationRequestDto()
                .setOperationType(OperationType.DELETE_PROCESS_INSTANCE)
                .setQuery(new ListViewQueryDto().setCompleted(true).setFinished(true)));

    assertThat(batchOperationEntity).isNotNull();
    verify(batchOperationWriter, times(1)).scheduleBatchOperation(any());
  }

  @Test
  public void testWritePermissionsForSingleOperation() {
    // given
    when(batchOperationWriter.scheduleSingleOperation(anyLong(), any()))
        .thenReturn(new BatchOperationEntity());
    final var processId = "23";
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(processId));
    when(permissionsService.hasPermissionForProcess(
            processId, PermissionType.DELETE_PROCESS_INSTANCE))
        .thenReturn(true);

    final BatchOperationEntity batchOperationEntity =
        processInstanceRestService.operation(
            processId,
            new CreateOperationRequestDto()
                .setOperationType(OperationType.DELETE_PROCESS_INSTANCE));

    assertThat(batchOperationEntity).isNotNull();
    verify(batchOperationWriter, times(1)).scheduleSingleOperation(anyLong(), any());
  }
}
