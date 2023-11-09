/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.reader;

import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import org.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;

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

   List<DurationChartEntryDto> getCountByDurationChart(final FlowNodeOutlierParametersDto outlierParams);

   Map<String, FindingsDto> getFlowNodeOutlierMap(final ProcessDefinitionParametersDto processDefinitionParams);

   List<VariableTermDto> getSignificantOutlierVariableTerms(final FlowNodeOutlierParametersDto outlierParams);

   List<ProcessInstanceIdDto> getSignificantOutlierVariableTermsInstanceIds(final FlowNodeOutlierVariableParametersDto outlierParams);
}
