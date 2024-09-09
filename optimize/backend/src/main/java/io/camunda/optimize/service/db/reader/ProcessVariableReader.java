/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.util.LogUtil.sanitizeLogMessage;

import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import io.camunda.optimize.dto.optimize.query.variable.DefinitionVariableLabelsDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessToQueryDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameRequestDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableSourceDto;
import io.camunda.optimize.dto.optimize.query.variable.ProcessVariableValuesQueryDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.util.LogUtil;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Component
@Slf4j
public class ProcessVariableReader {

  private final VariableLabelReader variableLabelReader;
  private final VariableRepository variableRepository;

  public List<ProcessVariableNameResponseDto> getVariableNames(
      final ProcessVariableNameRequestDto variableNameRequest) {
    Map<String, List<String>> logEntries = new HashMap<>();
    variableNameRequest
        .getProcessesToQuery()
        .forEach(
            processToQuery ->
                logEntries.put(
                    sanitizeLogMessage(processToQuery.getProcessDefinitionKey()),
                    processToQuery.getProcessDefinitionVersions().stream()
                        .map(LogUtil::sanitizeLogMessage)
                        .collect(Collectors.toList())));
    log.debug("Fetching variable names for {definitionKey=[versions]}: [{}]", logEntries);

    final List<ProcessToQueryDto> validNameRequests =
        variableNameRequest.getProcessesToQuery().stream()
            .filter(request -> request.getProcessDefinitionKey() != null)
            .filter(request -> !CollectionUtils.isEmpty(request.getProcessDefinitionVersions()))
            .toList();
    if (validNameRequests.isEmpty()) {
      log.debug(
          "Cannot fetch variable names as no valid variable requests are provided. "
              + "Variable requests must include definition key and version.");
      return Collections.emptyList();
    }

    List<String> processDefinitionKeys =
        validNameRequests.stream()
            .map(ProcessToQueryDto::getProcessDefinitionKey)
            .distinct()
            .toList();

    Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos =
        variableLabelReader.getVariableLabelsByKey(processDefinitionKeys);

    return variableRepository.getVariableNames(
        variableNameRequest, validNameRequests, processDefinitionKeys, definitionLabelsDtos);
  }

  public List<ProcessVariableNameResponseDto> getVariableNamesForInstancesMatchingQuery(
      final List<String> processDefinitionKeysToTarget,
      final BoolQueryBuilder baseQuery,
      final Map<String, DefinitionVariableLabelsDto> definitionLabelsDtos) {
    return variableRepository.getVariableNamesForInstancesMatchingQuery(
        processDefinitionKeysToTarget, baseQuery, definitionLabelsDtos);
  }

  public List<String> getVariableValues(final ProcessVariableValuesQueryDto requestDto) {
    final List<ProcessVariableSourceDto> processVariableSources =
        requestDto.getProcessVariableSources().stream()
            .filter(source -> !CollectionUtils.isEmpty(source.getProcessDefinitionVersions()))
            .collect(Collectors.toList());
    if (processVariableSources.isEmpty()) {
      log.debug("Cannot fetch variable values for process definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug("Fetching input variable values from sources [{}]", processVariableSources);

    return variableRepository.getVariableValues(requestDto, processVariableSources);
  }
}
