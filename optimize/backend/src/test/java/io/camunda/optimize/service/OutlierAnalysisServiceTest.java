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
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
import io.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class OutlierAnalysisServiceTest {

  private static final String USER_ID = "unauthorized-user";
  private static final String PROCESS_DEFINITION_KEY = "test-process";
  private static final List<String> TENANT_IDS = List.of("tenant1");

  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService =
      mock(DataSourceDefinitionAuthorizationService.class);
  private final DurationOutliersReader outliersReader = mock(DurationOutliersReader.class);
  private final OutlierAnalysisService outlierAnalysisService =
      new OutlierAnalysisService(definitionAuthorizationService, outliersReader);

  @Test
  void shouldReturnFlowNodeOutlierMapWhenUserIsAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new ProcessDefinitionParametersDto(), true);
    final Map<String, FindingsDto> expectedResult = Map.of("node1", new FindingsDto());

    when(outliersReader.getFlowNodeOutlierMap(params)).thenReturn(expectedResult);

    // when
    final Map<String, FindingsDto> result = outlierAnalysisService.getFlowNodeOutlierMap(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldReturnCountByDurationChartWhenUserIsAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierParametersDto(), true);

    final List<DurationChartEntryDto> expectedResult = List.of(new DurationChartEntryDto());
    when(outliersReader.getCountByDurationChart(params)).thenReturn(expectedResult);

    // when
    final List<DurationChartEntryDto> result =
        outlierAnalysisService.getCountByDurationChart(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldReturnSignificantOutlierVariableTermsWhenUserIsAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierParametersDto(), true);

    final List<VariableTermDto> expectedResult = List.of(new VariableTermDto());
    when(outliersReader.getSignificantOutlierVariableTerms(params)).thenReturn(expectedResult);

    // when
    final List<VariableTermDto> result =
        outlierAnalysisService.getSignificantOutlierVariableTerms(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldReturnSignificantOutlierVariableTermsInstanceIdsWhenUserIsAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierVariableParametersDto(), true);

    final List<ProcessInstanceIdDto> expectedResult = List.of(new ProcessInstanceIdDto());
    when(outliersReader.getSignificantOutlierVariableTermsInstanceIds(params))
        .thenReturn(expectedResult);

    // when
    final List<ProcessInstanceIdDto> result =
        outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(params);

    // then
    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void shouldThrowForbiddenExceptionFlowNodeOutlierMapWhenUserIsNotAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new ProcessDefinitionParametersDto(), false);

    // when/then
    assertThatThrownBy(() -> outlierAnalysisService.getFlowNodeOutlierMap(params))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage(
            "Current user is not authorized to access data of the provided process definition and tenant combination");
  }

  @Test
  void shouldThrowForbiddenExceptionCountByDurationChartWhenUserIsNotAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierParametersDto(), false);

    // when/then
    assertThatThrownBy(() -> outlierAnalysisService.getCountByDurationChart(params))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage(
            "Current user is not authorized to access data of the provided process definition and tenant combination");
  }

  @Test
  void shouldThrowForbiddenExceptionSignificantOutlierVariableTermsWhenUserIsNotAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierParametersDto(), false);
    // when/then
    assertThatThrownBy(() -> outlierAnalysisService.getSignificantOutlierVariableTerms(params))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage(
            "Current user is not authorized to access data of the provided process definition and tenant combination");
  }

  @Test
  void shouldThrowForbiddenExceptionSignificantVariableTermsInstanceIdsWhenUserIsNotAuthorized() {
    // given
    final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> params =
        getAnalysisServiceParametersWithAuthMock(new FlowNodeOutlierVariableParametersDto(), false);

    // when/then
    assertThatThrownBy(
            () -> outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(params))
        .isInstanceOf(ForbiddenException.class)
        .hasMessage(
            "Current user is not authorized to access data of the provided process definition and tenant combination");
  }

  private <T extends ProcessDefinitionParametersDto>
      OutlierAnalysisServiceParameters<T> getAnalysisServiceParametersWithAuthMock(
          T processDefinitionParams, boolean auth) {
    when(definitionAuthorizationService.isAuthorizedToAccessDefinition(
            USER_ID, DefinitionType.PROCESS, PROCESS_DEFINITION_KEY, TENANT_IDS))
        .thenReturn(auth);
    processDefinitionParams.setProcessDefinitionKey(PROCESS_DEFINITION_KEY);
    processDefinitionParams.setTenantIds(TENANT_IDS);
    return new OutlierAnalysisServiceParameters<>(
        processDefinitionParams, ZoneOffset.systemDefault(), USER_ID);
  }
}
