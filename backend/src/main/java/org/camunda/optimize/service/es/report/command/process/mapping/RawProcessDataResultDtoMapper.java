/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.variable.value.VariableInstanceDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RawProcessDataResultDtoMapper {
  private static final Logger logger = LoggerFactory.getLogger(RawProcessDataResultDtoMapper.class);

  private final Long recordLimit;

  public RawProcessDataResultDtoMapper(final Long recordLimit) {
    this.recordLimit = recordLimit;
  }

  public RawDataProcessReportResultDto mapFrom(final SearchResponse searchResponse, final ObjectMapper objectMapper) {
    List<RawDataProcessInstanceDto> rawData = new ArrayList<>();
    Set<String> allVariableNames = new HashSet<>();
    SearchHits searchHits = searchResponse.getHits();

    Arrays.stream(searchHits.getHits())
      .limit(recordLimit)
      .forEach(hit -> {
        final String sourceAsString = hit.getSourceAsString();
        try {
          final ProcessInstanceDto processInstanceDto = objectMapper.readValue(
            sourceAsString,
            ProcessInstanceDto.class
          );

          Map<String, Object> variables = getVariables(processInstanceDto, objectMapper);
          allVariableNames.addAll(variables.keySet());
          RawDataProcessInstanceDto dataEntry = convertToRawDataEntry(processInstanceDto, variables);
          rawData.add(dataEntry);
        } catch (IOException e) {
          logger.error("can't map process instance {}", sourceAsString, e);
        }
      });

    ensureEveryRawDataInstanceContainsAllVariableNames(rawData, allVariableNames);

    return createResult(rawData, searchHits.getTotalHits());
  }

  private void ensureEveryRawDataInstanceContainsAllVariableNames(final List<RawDataProcessInstanceDto> rawData,
                                                                  final Set<String> allVariableNames) {
    rawData
      .forEach(data -> allVariableNames
        .forEach(varName -> data.getVariables().putIfAbsent(varName, ""))
      );
  }

  private RawDataProcessInstanceDto convertToRawDataEntry(final ProcessInstanceDto processInstanceDto,
                                                          final Map<String, Object> variables) {
    return new RawDataProcessInstanceDto(
      processInstanceDto.getProcessDefinitionKey(),
      processInstanceDto.getProcessDefinitionId(),
      processInstanceDto.getProcessInstanceId(),
      processInstanceDto.getBusinessKey(),
      processInstanceDto.getStartDate(),
      processInstanceDto.getEndDate(),
      processInstanceDto.getEngine(),
      processInstanceDto.getTenantId(),
      variables
    );
  }

  private Map<String, Object> getVariables(final ProcessInstanceDto processInstanceDto,
                                           final ObjectMapper objectMapper) {
    Map<String, Object> result = new TreeMap<>();

    for (VariableInstanceDto instance : processInstanceDto.obtainAllVariables()) {
      if (instance.getName() != null) {
        result.put(instance.getName(), instance.getValue());
      } else {
        try {
          logger.debug("Found variable with null name [{}]", objectMapper.writeValueAsString(instance));
        } catch (JsonProcessingException e) {
          //nothing to do
        }
      }

    }
    return result;
  }

  private RawDataProcessReportResultDto createResult(final List<RawDataProcessInstanceDto> limitedRawDataResult,
                                                     final Long totalHits) {
    final RawDataProcessReportResultDto result = new RawDataProcessReportResultDto();
    result.setData(limitedRawDataResult);
    result.setIsComplete(limitedRawDataResult.size() == totalHits);
    result.setProcessInstanceCount(totalHits);
    return result;
  }

}
