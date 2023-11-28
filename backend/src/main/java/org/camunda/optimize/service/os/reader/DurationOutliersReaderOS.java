/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.reader;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.service.db.reader.DurationOutliersReader;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DurationOutliersReaderOS implements DurationOutliersReader {

  @Override
  public List<DurationChartEntryDto> getCountByDurationChart(final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public Map<String, FindingsDto> getFlowNodeOutlierMap(final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> outlierAnalysisParams) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<VariableTermDto> getSignificantOutlierVariableTerms(final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    //todo will be handled in the OPT-7230
    return null;
  }

  @Override
  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> outlierParams) {
    //todo will be handled in the OPT-7230
    return null;
  }

}
