/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

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
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OutlierAnalysisService {

  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final DurationOutliersReader outliersReader;

  public OutlierAnalysisService(
      final DataSourceDefinitionAuthorizationService definitionAuthorizationService,
      final DurationOutliersReader outliersReader) {
    this.definitionAuthorizationService = definitionAuthorizationService;
    this.outliersReader = outliersReader;
  }

  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto>
          outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getFlowNodeOutlierMap(outlierAnalysisParams);
  }

  public List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getCountByDurationChart(outlierAnalysisParams);
  }

  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getSignificantOutlierVariableTerms(outlierAnalysisParams);
  }

  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto>
          outlierAnalysisParams) {
    doAuthorizationCheck(outlierAnalysisParams);
    return outliersReader.getSignificantOutlierVariableTermsInstanceIds(outlierAnalysisParams);
  }

  private <T extends ProcessDefinitionParametersDto> void doAuthorizationCheck(
      final OutlierAnalysisServiceParameters<T> outlierAnalysisParams) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
        outlierAnalysisParams.getUserId(),
        DefinitionType.PROCESS,
        outlierAnalysisParams.getProcessDefinitionParametersDto().getProcessDefinitionKey(),
        outlierAnalysisParams.getProcessDefinitionParametersDto().getTenantIds())) {
      throw new ForbiddenException(
          "Current user is not authorized to access data of the provided process definition and tenant combination");
    }
  }
}
