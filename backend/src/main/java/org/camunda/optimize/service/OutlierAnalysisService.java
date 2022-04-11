/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.service.es.reader.DurationOutliersReader;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.ForbiddenException;
import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
public class OutlierAnalysisService {

  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final DurationOutliersReader outliersReader;

  public Map<String, FindingsDto> getFlowNodeOutlierMap(final ProcessDefinitionParametersDto processDefinitionParams,
                                                        final String userId) {
    doAuthorizationCheck(processDefinitionParams, userId);
    return outliersReader.getFlowNodeOutlierMap(processDefinitionParams);
  }

  public List<DurationChartEntryDto> getCountByDurationChart(final FlowNodeOutlierParametersDto outlierParams,
                                                             final String userId) {
    doAuthorizationCheck(outlierParams, userId);
    return outliersReader.getCountByDurationChart(outlierParams);
  }

  public List<VariableTermDto> getSignificantOutlierVariableTerms(final FlowNodeOutlierParametersDto outlierParams,
                                                                  final String userId) {
    doAuthorizationCheck(outlierParams, userId);

    return outliersReader.getSignificantOutlierVariableTerms(outlierParams);
  }

  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(final FlowNodeOutlierVariableParametersDto outlierParams,
                                                                                  final String userId) {
    doAuthorizationCheck(outlierParams, userId);
    return outliersReader.getSignificantOutlierVariableTermsInstanceIds(outlierParams);
  }

  private void doAuthorizationCheck(final ProcessDefinitionParametersDto processDefinitionParams, final String userId) {
    if (!definitionAuthorizationService.isAuthorizedToAccessDefinition(
      userId, DefinitionType.PROCESS, processDefinitionParams.getProcessDefinitionKey(), processDefinitionParams.getTenantIds()
    )) {
      throw new ForbiddenException(
        "Current user is not authorized to access data of the provided process definition and tenant combination");
    }
  }
}
