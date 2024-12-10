/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import jakarta.ws.rs.ForbiddenException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OutlierAnalysisServiceTest {
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
      mock(DataSourceDefinitionAuthorizationService.class);
  private final DurationOutliersReader outliersReader = mock(DurationOutliersReader.class);
  private final OutlierAnalysisService outlierAnalysisService =
      new OutlierAnalysisService(definitionAuthorizationService, outliersReader);

  @Test
  void shouldReturnFlowNodeOutlierMapWhenUserIsAuthorized() {
    // given
    final String userId = "test-user";
    final String processDefinitionKey = "test-process";
    final List<String> tenantIds = List.of("tenant1");
    final ProcessDefinitionParametersDto processDefinitionParams =
        new ProcessDefinitionParametersDto();
    processDefinitionParams.setProcessDefinitionKey(processDefinitionKey);
    processDefinitionParams.setTenantIds(tenantIds);
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> params =
        new OutlierAnalysisServiceParameters<>(
            processDefinitionParams, ZoneOffset.systemDefault(), userId);

    final Map<String, FindingsDto> expectedResult = Map.of("node1", new FindingsDto());
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, DefinitionType.PROCESS, processDefinitionKey, tenantIds))
        .thenReturn(true);
    when(outliersReader.getFlowNodeOutlierMap(params)).thenReturn(expectedResult);

    // when
    final Map<String, FindingsDto> result = outlierAnalysisService.getFlowNodeOutlierMap(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldThrowForbiddenExceptionWhenUserIsNotAuthorized() {
    // given
    final String userId = "unauthorized-user";
    final String processDefinitionKey = "test-process";
    final List<String> tenantIds = List.of("tenant1");
    final ProcessDefinitionParametersDto processDefinitionParams =
        new ProcessDefinitionParametersDto();
    processDefinitionParams.setTenantIds(tenantIds);
    processDefinitionParams.setProcessDefinitionKey(processDefinitionKey);
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> params =
        new OutlierAnalysisServiceParameters<>(
            processDefinitionParams, ZoneOffset.systemDefault(), userId);

    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, DefinitionType.PROCESS, processDefinitionKey, tenantIds))
        .thenReturn(false);

    // when/then
    assertThatThrownBy(() -> outlierAnalysisService.getFlowNodeOutlierMap(params))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage(
            "Current user is not authorized to access data of the provided process definition and tenant combination");
  }

  @Test
  void shouldReturnCountByDurationChartWhenUserIsAuthorized() {
    // given
    final String userId = "test-user";
    final String processDefinitionKey = "test-process";
    final List<String> tenantIds = List.of("tenant1");
    final FlowNodeOutlierParametersDto flowNodeParams = new FlowNodeOutlierParametersDto();
    flowNodeParams.setTenantIds(tenantIds);
    flowNodeParams.setProcessDefinitionKey(processDefinitionKey);
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> params =
        new OutlierAnalysisServiceParameters<>(flowNodeParams, ZoneOffset.systemDefault(), userId);

    final List<DurationChartEntryDto> expectedResult = List.of(new DurationChartEntryDto());
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            userId, DefinitionType.PROCESS, processDefinitionKey, tenantIds))
        .thenReturn(true);
    when(outliersReader.getCountByDurationChart(params)).thenReturn(expectedResult);

    // when
    final List<DurationChartEntryDto> result =
        outlierAnalysisService.getCountByDurationChart(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }
}
