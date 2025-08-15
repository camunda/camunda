/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.JacksonConfig;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.webapp.reader.DecisionInstanceReader;
import io.camunda.operate.webapp.rest.DecisionInstanceRestService;
import io.camunda.operate.webapp.rest.dto.dmn.DRDDataEntryDto;
import io.camunda.operate.webapp.rest.dto.dmn.DecisionInstanceDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import io.camunda.operate.webapp.security.permission.PermissionsService;
import io.camunda.webapps.schema.entities.dmn.DecisionInstanceState;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      DecisionInstanceRestService.class,
      OperateProfileService.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperatePropertiesOverride.class,
      UnifiedConfigurationHelper.class,
      UnifiedConfiguration.class
    })
public class DecisionInstanceRestServiceIT extends OperateAbstractIT {

  @MockBean private DecisionInstanceReader decisionInstanceReader;

  @MockBean private PermissionsService permissionsService;

  @Test
  public void testGetDecisionInstanceByWrongId() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenThrow(
            new NotFoundException(
                String.format("Decision instance not found: " + decisionInstanceId)));
    when(permissionsService.permissionsEnabled()).thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithException(
            getDecisionInstanceByIdUrl(decisionInstanceId), NotFoundException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Decision instance not found: " + decisionInstanceId);
  }

  @Test
  public void testGetDecisionInstanceDrdDataByWrongId() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId)).thenReturn(null);
    when(permissionsService.permissionsEnabled()).thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithException(
            getDecisionInstanceDrdByIdUrl(decisionInstanceId), NotFoundException.class);
    assertThat(mvcResult.getResolvedException().getMessage())
        .contains("Decision instance not found: " + decisionInstanceId);
  }

  @Test
  public void testDecisionInstanceFailsWhenNoPermissions() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    final String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenReturn(new DecisionInstanceDto().setDecisionId(bpmnDecisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(
            bpmnDecisionId, PermissionType.READ_DECISION_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getDecisionInstanceByIdUrl(decisionInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision instance");
  }

  @Test
  public void testDecisionInstanceDrdFailsWhenNoPermissions() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    final String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenReturn(new DecisionInstanceDto().setDecisionId(bpmnDecisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(true);
    when(permissionsService.hasPermissionForDecision(
            bpmnDecisionId, PermissionType.READ_DECISION_INSTANCE))
        .thenReturn(false);
    final MvcResult mvcResult =
        getRequestShouldFailWithNoAuthorization(getDecisionInstanceDrdByIdUrl(decisionInstanceId));
    // then
    assertErrorMessageContains(mvcResult, "No read permission for decision instance");
  }

  @Test
  public void testDecisionInstanceSucceedsWhenPermissions() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    final String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstance(decisionInstanceId))
        .thenReturn(new DecisionInstanceDto().setDecisionId(bpmnDecisionId));
    when(permissionsService.permissionsEnabled()).thenReturn(false);
    final MvcResult mvcResult = getRequest(getDecisionInstanceByIdUrl(decisionInstanceId));
    // then
    final DecisionInstanceDto result =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(result.getDecisionId()).isEqualTo(bpmnDecisionId);
  }

  @Test
  public void testDecisionInstanceDrdSucceedsWhenPermissions() throws Exception {
    // given
    final String decisionInstanceId = "instanceId";
    final String bpmnDecisionId = "decisionId";
    // when
    when(decisionInstanceReader.getDecisionInstanceDRDData(decisionInstanceId))
        .thenReturn(
            Map.of(
                bpmnDecisionId,
                List.of(
                    new DRDDataEntryDto(
                        decisionInstanceId, bpmnDecisionId, DecisionInstanceState.EVALUATED))));
    when(permissionsService.permissionsEnabled()).thenReturn(false);
    final MvcResult mvcResult = getRequest(getDecisionInstanceDrdByIdUrl(decisionInstanceId));
    final Map<String, List<DRDDataEntryDto>> result =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    // then
    assertThat(result.get(bpmnDecisionId).getFirst().getDecisionInstanceId())
        .isEqualTo(decisionInstanceId);
  }

  public String getDecisionInstanceByIdUrl(final String id) {
    return DecisionInstanceRestService.DECISION_INSTANCE_URL + "/" + id;
  }

  public String getDecisionInstanceDrdByIdUrl(final String id) {
    return DecisionInstanceRestService.DECISION_INSTANCE_URL + "/" + id + "/drd-data";
  }
}
