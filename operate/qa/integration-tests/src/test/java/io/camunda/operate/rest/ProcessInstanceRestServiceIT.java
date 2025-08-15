/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false"
    })
@WebAppConfiguration
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public class ProcessInstanceRestServiceIT {
  @Autowired MockMvcManager mockMvcManager;
  @MockitoBean PermissionsService mockPermissionsService;
  @MockitoBean ProcessInstanceReader mockProcessInstanceReader;

  @Test
  public void testGetInstanceByIdWithInvalidId() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/4503599627535750:";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetIncidentsByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/incidents";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetVariablesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/variables";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-states";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeMetadataByIdWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-metadata";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetListenersWithInvalidId() throws Exception {
    final String url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/listeners";
    final MvcResult mvcResult =
        mockMvcManager.postRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testListenersRequestWithFlowNodeIdAndFlowNodeInstanceIdInvalid() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/1/listeners";
    final ListenerRequestDto request =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("testid")
            .setFlowNodeInstanceId(123L);
    final MvcResult mvcResult = mockMvcManager.postRequest(url, request, 400);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Only one of 'flowNodeId' or 'flowNodeInstanceId'");
  }

  @Test
  public void testListenersRequestWithListenerFilterInvalid() throws Exception {
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/1/listeners";
    final ListenerRequestDto request =
        new ListenerRequestDto()
            .setPageSize(20)
            .setFlowNodeId("testid")
            .setListenerTypeFilter(ListenerType.UNKNOWN);
    final MvcResult mvcResult = mockMvcManager.postRequest(url, request, 400);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains(
            "'listenerTypeFilter' only allows for values: ["
                + "null, "
                + ListenerType.EXECUTION_LISTENER
                + ", "
                + ListenerType.TASK_LISTENER
                + "]");
  }

  @Test
  public void testPostUnsupportedOperationDeleteDecisionRequest() throws Exception {
    // given
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/1/operation";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.DELETE_DECISION_DEFINITION);

    // when - then
    final MvcResult mvcResult = mockMvcManager.postRequest(url, request, 400);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains(
            "Operation type '%s' is not supported by this endpoint."
                .formatted(OperationType.DELETE_DECISION_DEFINITION));
  }

  @Test
  public void testPostUnsupportedOperationDeleteProcessRequest() throws Exception {
    // given
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/1/operation";
    final CreateOperationRequestDto request =
        new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_DEFINITION);

    // when - then
    final MvcResult mvcResult = mockMvcManager.postRequest(url, request, 400);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains(
            "Operation type '%s' is not supported by this endpoint."
                .formatted(OperationType.DELETE_PROCESS_DEFINITION));
  }

  @ParameterizedTest
  @MethodSource("noPermissionPostParameters")
  public void testPostWithNoPermission(
      final String urlFragment, final Object requestObject, final PermissionType permissionType)
      throws Exception {
    // given
    Mockito.when(mockProcessInstanceReader.getProcessInstanceByKey(1L))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("p1"));
    Mockito.when(mockPermissionsService.hasPermissionForProcess("p1", permissionType))
        .thenReturn(false);
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + urlFragment;

    // when - then
    final MvcResult mvcResult = mockMvcManager.postRequest(url, requestObject, 403);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("No %s permission for process instance".formatted(permissionType));
  }

  @ParameterizedTest
  @MethodSource("noPermissionGetParameters")
  public void testGetWithNoPermission(final String urlFragment) throws Exception {
    Mockito.when(mockProcessInstanceReader.getProcessInstanceByKey(1L))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("p1"));
    Mockito.when(
            mockPermissionsService.hasPermissionForProcess(
                "p1", PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);
    final String url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + urlFragment;
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, NotAuthorizedException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains(
            "No %s permission for process instance"
                .formatted(PermissionType.READ_PROCESS_INSTANCE));
  }

  private static Stream<Arguments> noPermissionGetParameters() {
    return Stream.of(
        Arguments.of("/1"),
        Arguments.of("/1/incidents"),
        Arguments.of("/1/sequence-flows"),
        Arguments.of("/1/variables/1"),
        Arguments.of("/1/variables/1"),
        Arguments.of("/1/flow-node-states"),
        Arguments.of("/1/statistics"));
  }

  private static Stream<Arguments> noPermissionPostParameters() {
    return Stream.of(
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto().setOperationType(OperationType.DELETE_PROCESS_INSTANCE),
            PermissionType.DELETE_PROCESS_INSTANCE),
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto().setOperationType(OperationType.CANCEL_PROCESS_INSTANCE),
            PermissionType.CANCEL_PROCESS_INSTANCE),
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto().setOperationType(OperationType.RESOLVE_INCIDENT),
            PermissionType.UPDATE_PROCESS_INSTANCE),
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto().setOperationType(OperationType.ADD_VARIABLE),
            PermissionType.UPDATE_PROCESS_INSTANCE),
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto().setOperationType(OperationType.UPDATE_VARIABLE),
            PermissionType.UPDATE_PROCESS_INSTANCE),
        Arguments.of(
            "/1/operation",
            new CreateOperationRequestDto()
                .setOperationType(OperationType.MIGRATE_PROCESS_INSTANCE),
            PermissionType.UPDATE_PROCESS_INSTANCE),
        Arguments.of(
            "/1/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(
                        new Modification().setModification(Type.ADD_TOKEN).setToFlowNodeId("fid"))),
            PermissionType.MODIFY_PROCESS_INSTANCE),
        Arguments.of(
            "/1/variables",
            new VariableRequestDto().setScopeId("scope"),
            PermissionType.READ_PROCESS_INSTANCE),
        Arguments.of(
            "/1/listeners",
            new ListenerRequestDto().setPageSize(5).setFlowNodeId("fid"),
            PermissionType.READ_PROCESS_INSTANCE),
        Arguments.of(
            "/1/flow-node-metadata",
            new FlowNodeMetadataRequestDto().setFlowNodeId("fid"),
            PermissionType.READ_PROCESS_INSTANCE));
  }
}
