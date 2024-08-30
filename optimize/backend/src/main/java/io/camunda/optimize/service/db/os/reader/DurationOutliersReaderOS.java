/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import io.camunda.optimize.service.db.reader.DurationOutliersReader;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class DurationOutliersReaderOS implements DurationOutliersReader {

  @Override
  public List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto>
          outlierAnalysisParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }

  @Override
  public List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> outlierParams) {
    log.debug("Functionality not implemented for OpenSearch");
    return null;
  }
}
