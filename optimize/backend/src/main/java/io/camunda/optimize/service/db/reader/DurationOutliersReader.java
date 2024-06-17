/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import java.util.List;
import java.util.Map;

public interface DurationOutliersReader {

  String AGG_HISTOGRAM = "histogram";
  String AGG_STATS = "stats";
  String AGG_FILTERED_FLOW_NODES = "filteredFlowNodes";
  String AGG_NESTED = "nested";
  String AGG_REVERSE_NESTED_PROCESS_INSTANCE = "processInstance";
  String AGG_VARIABLES = "variables";
  String AGG_VARIABLE_VALUE_TERMS = "variableValueTerms";
  String LOWER_DURATION_AGG = "lowerDurationAgg";
  String HIGHER_DURATION_AGG = "higherDurationAgg";
  String FLOW_NODE_ID_AGG = "flowNodeId";
  String FLOW_NODE_TYPE_FILTER = "flowNodeTypeFilter";

  List<DurationChartEntryDto> getCountByDurationChart(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams);

  Map<String, FindingsDto> getFlowNodeOutlierMap(
      final OutlierAnalysisServiceParameters<ProcessDefinitionParametersDto> outlierAnalysisParams);

  List<VariableTermDto> getSignificantOutlierVariableTerms(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierParametersDto> outlierAnalysisParams);

  List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(
      final OutlierAnalysisServiceParameters<FlowNodeOutlierVariableParametersDto> outlierParams);
}
