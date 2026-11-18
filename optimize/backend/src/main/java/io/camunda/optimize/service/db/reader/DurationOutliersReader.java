/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_MANUAL_TASK;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.FLOW_NODE_TYPE_USER_TASK;

import io.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import io.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.FlowNodeOutlierVariableParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.OutlierAnalysisServiceParameters;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessDefinitionParametersDto;
import io.camunda.optimize.dto.optimize.query.analysis.ProcessInstanceIdDto;
import io.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.math3.stat.inference.TestUtils;

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

  default List<VariableTermDto> mapToVariableTermList(
      final Map<String, Map<String, Long>> outlierSignificantVariableTerms,
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
      final long outlierProcessInstanceCount,
      final long nonOutlierProcessInstanceCount,
      final long totalProcessInstanceCount) {

    return outlierSignificantVariableTerms.entrySet().stream()
        .flatMap(
            significantVariableTerms ->
                significantVariableTerms.getValue().entrySet().stream()
                    .map(
                        termAndCount -> {
                          final String variableName = significantVariableTerms.getKey();
                          final Long outlierTermOccurrence = termAndCount.getValue();
                          return new VariableTermDto(
                              variableName,
                              termAndCount.getKey(),
                              outlierTermOccurrence,
                              getRatio(outlierProcessInstanceCount, outlierTermOccurrence),
                              Optional.ofNullable(
                                      nonOutlierVariableTermOccurrence.get(variableName))
                                  .flatMap(
                                      entry ->
                                          Optional.ofNullable(entry.get(termAndCount.getKey())))
                                  .map(
                                      nonOutlierTermOccurrence ->
                                          getRatio(
                                              nonOutlierProcessInstanceCount,
                                              nonOutlierTermOccurrence))
                                  .orElse(0.0D),
                              getRatio(totalProcessInstanceCount, outlierTermOccurrence));
                        }))
        .sorted(Comparator.comparing(VariableTermDto::getInstanceCount).reversed())
        .collect(Collectors.toList());
  }

  default double getRatio(final long totalCount, final long observedCount) {
    return (double) observedCount / totalCount;
  }

  default String getFilteredFlowNodeAggregationName(final String flowNodeId) {
    return AGG_FILTERED_FLOW_NODES + flowNodeId;
  }

  default boolean isOutlier(
      final Long lowerOutlierBound, final Long higherOutlierBound, final Long durationValue) {
    return Optional.ofNullable(lowerOutlierBound).map(value -> durationValue < value).orElse(false)
        || Optional.ofNullable(higherOutlierBound)
            .map(value -> durationValue > value)
            .orElse(false);
  }

  default Map<String, Map<String, Long>> filterSignificantOutlierVariableTerms(
      final Map<String, Map<String, Long>> outlierVariableTermOccurrences,
      final Map<String, Map<String, Long>> nonOutlierVariableTermOccurrence,
      final long outlierProcessInstanceCount,
      final long nonOutlierProcessInstanceCount) {

    return outlierVariableTermOccurrences.entrySet().stream()
        .map(
            outlierVariableTermOccurrence -> {
              final String variableName = outlierVariableTermOccurrence.getKey();
              final Map<String, Long> outlierTermOccurrences =
                  outlierVariableTermOccurrence.getValue();
              final Map<String, Long> nonOutlierTermOccurrences =
                  nonOutlierVariableTermOccurrence.getOrDefault(
                      variableName, Collections.emptyMap());

              final Map<String, Long> significantTerms =
                  outlierTermOccurrences.entrySet().stream()
                      .filter(
                          outlierTermAndCount -> {
                            final String term = outlierTermAndCount.getKey();
                            final Long outlierTermCount = outlierTermAndCount.getValue();
                            final Long nonOutlierTermCount =
                                nonOutlierTermOccurrences.getOrDefault(term, 0L);

                            final boolean isMoreFrequentInOutlierSet =
                                getRatio(outlierProcessInstanceCount, outlierTermCount)
                                    > getRatio(nonOutlierProcessInstanceCount, nonOutlierTermCount);

                            final boolean isSignificant =
                                TestUtils.chiSquareTestDataSetsComparison(
                                    new long[] {
                                      nonOutlierTermCount, nonOutlierProcessInstanceCount
                                    },
                                    new long[] {outlierTermCount, outlierProcessInstanceCount},
                                    // This is the confidence level or alpha that defines the degree
                                    // of confidence of the test result.
                                    // The test returns true if the null hypothesis (both datasets
                                    // originate from the same distribution)
                                    // can be rejected with 100 * (1 - alpha) percent confidence and
                                    // thus the sets can be considered
                                    // to be significantly different
                                    0.001D);

                            return isMoreFrequentInOutlierSet && isSignificant;
                          })
                      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
              return new AbstractMap.SimpleEntry<>(variableName, significantTerms);
            })
        .filter(stringMapSimpleEntry -> !stringMapSimpleEntry.getValue().isEmpty())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  default List<String> generateListOfHumanTasks() {
    return List.of(FLOW_NODE_TYPE_USER_TASK, FLOW_NODE_TYPE_MANUAL_TASK);
  }
}
