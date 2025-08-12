/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.configuration.beanoverrides.SearchEngineConnectPropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.FlowNodeInstanceRestService;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      FlowNodeInstanceRestService.class,
      OperatePropertiesOverride.class,
      SearchEngineConnectPropertiesOverride.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    })
public class FlowNodeInstanceRestServiceIT extends OperateAbstractIT {

  @MockBean protected ProcessInstanceReader processInstanceReader;
  @MockBean private FlowNodeInstanceReader flowNodeInstanceReader;
  @MockBean private PermissionsService permissionsService;

  @Test
  public void testFlowNodeInstancesFailsWhenNoPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String treePath = "456";
    final String bpmnProcessId = "processId";
    final FlowNodeInstanceRequestDto requestDto =
        (new FlowNodeInstanceRequestDto())
            .setQueries(
                List.of(
                    new FlowNodeInstanceQueryDto()
                        .setProcessInstanceId(processInstanceId)
                        .setTreePath(treePath)));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));

    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        postRequestShouldFailWithNoAuthorization(FLOW_NODE_INSTANCE_URL, requestDto);
    // then
    assertErrorMessageContains(mvcResult, "No read permission for process instance");
  }

  @Test
  public void testFlowNodeInstancesOkWhenHasPermissions() throws Exception {
    // given
    final String processInstanceId = "123";
    final String treePath = "456";
    final String bpmnProcessId = "processId";
    final FlowNodeInstanceRequestDto requestDto =
        (new FlowNodeInstanceRequestDto())
            .setQueries(
                List.of(
                    new FlowNodeInstanceQueryDto()
                        .setProcessInstanceId(processInstanceId)
                        .setTreePath(treePath)));
    // when
    when(processInstanceReader.getProcessInstanceByKey(Long.valueOf(processInstanceId)))
        .thenReturn(new ProcessInstanceForListViewEntity().setBpmnProcessId(bpmnProcessId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForProcess(
            bpmnProcessId, PermissionType.READ_PROCESS_INSTANCE))
        .thenReturn(true);
    when(flowNodeInstanceReader.getFlowNodeInstances(requestDto)).thenReturn(new LinkedHashMap<>());
    final MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, requestDto);
    final Map<String, FlowNodeInstanceResponseDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    // then
    assertThat(response).isEmpty();
  }
}
