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
import static org.mockito.Mockito.when;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.DependencyInjectionTestExecutionListener;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.j5templates.MockMvcManager;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.operate.webapp.rest.exception.NotAuthorizedException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    final var url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/4503599627535750:";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);

    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @Test
  public void testGetFlowNodeStatesByIdWithInvalidId() throws Exception {
    final var url =
        ProcessInstanceRestService.PROCESS_INSTANCE_URL + "/not-valid-id-123/flow-node-states";
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, ConstraintViolationException.class);
    assertThat(mvcResult.getResolvedException().getMessage()).contains("Specified ID is not valid");
  }

  @ParameterizedTest
  @MethodSource("noPermissionPostParameters")
  public void testPostWithNoPermission(
      final String urlFragment, final Object requestObject, final PermissionType permissionType)
      throws Exception {
    // given
    when(mockProcessInstanceReader.getProcessInstanceByKey(1L))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("p1"));
    when(mockPermissionsService.hasPermissionForProcess("p1", permissionType)).thenReturn(false);
    final var url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + urlFragment;

    // when - then
    final MvcResult mvcResult = mockMvcManager.postRequest(url, requestObject, 403);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("No %s permission for process instance".formatted(permissionType));
  }

  @ParameterizedTest
  @MethodSource("noPermissionGetParameters")
  public void testGetWithNoPermission(final String urlFragment) throws Exception {
    when(mockProcessInstanceReader.getProcessInstanceByKey(1L))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId("p1"));
    when(mockPermissionsService.hasPermissionForProcess("p1", PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);
    final var url = ProcessInstanceRestService.PROCESS_INSTANCE_URL + urlFragment;
    final MvcResult mvcResult =
        mockMvcManager.getRequestShouldFailWithException(url, NotAuthorizedException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains(
            "No %s permission for process instance"
                .formatted(PermissionType.READ_PROCESS_INSTANCE));
  }

  private static Stream<Arguments> noPermissionGetParameters() {
    return Stream.of(Arguments.of("/1"), Arguments.of("/1/flow-node-states"));
  }

  private static Stream<Arguments> noPermissionPostParameters() {
    return Stream.of(
        Arguments.of(
            "/1/modify",
            new ModifyProcessInstanceRequestDto()
                .setModifications(
                    List.of(
                        new Modification().setModification(Type.ADD_TOKEN).setToFlowNodeId("fid"))),
            PermissionType.MODIFY_PROCESS_INSTANCE));
  }
}
