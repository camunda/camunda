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
import io.camunda.operate.webapp.rest.ProcessInstanceRestService;
import io.camunda.operate.webapp.rest.dto.ListenerRequestDto;
import io.camunda.webapps.schema.entities.listener.ListenerType;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {TestApplication.class, UnifiedConfigurationHelper.class, UnifiedConfiguration.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      OperateProperties.PREFIX + ".zeebe.compatibility.enabled = true",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER",
      OperateProperties.PREFIX + ".multiTenancy.enabled = false",
      "camunda.security.authorizations.enabled=false"
    })
@WebAppConfiguration
@TestExecutionListeners(
    listeners = DependencyInjectionTestExecutionListener.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@WithMockUser(DEFAULT_USER)
public class ProcessInstanceRestServiceIT {
  @Autowired MockMvcManager mockMvcManager;

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
}
