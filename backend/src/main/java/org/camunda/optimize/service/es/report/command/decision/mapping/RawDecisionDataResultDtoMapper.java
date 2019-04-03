package org.camunda.optimize.service.es.report.command.decision.mapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class RawDecisionDataResultDtoMapper {
  private static final Logger logger = LoggerFactory.getLogger(RawDecisionDataResultDtoMapper.class);

  private final Long recordLimit;

  public RawDecisionDataResultDtoMapper(final Long recordLimit) {
    this.recordLimit = recordLimit;
  }

  public RawDataDecisionReportResultDto mapFrom(final SearchResponse searchResponse, final ObjectMapper objectMapper) {
    final List<RawDataDecisionInstanceDto> rawData = new ArrayList<>();
    final Set<InputVariableEntry> allInputVariablesWithBlankValue = new LinkedHashSet<>();
    final Set<OutputVariableEntry> allOutputVariablesWithNoValues = new LinkedHashSet<>();

    final SearchHits searchHits = searchResponse.getHits();
    Arrays.stream(searchHits.getHits())
      .limit(recordLimit)
      .forEach(hit -> {
        final String sourceAsString = hit.getSourceAsString();
        try {
          final DecisionInstanceDto processInstanceDto = objectMapper.readValue(
            sourceAsString, DecisionInstanceDto.class
          );

          allInputVariablesWithBlankValue.addAll(getInputVariables(processInstanceDto));
          allOutputVariablesWithNoValues.addAll(getOutputVariables(processInstanceDto));

          RawDataDecisionInstanceDto dataEntry = convertToRawDataEntry(processInstanceDto);
          rawData.add(dataEntry);
        } catch (IOException e) {
          logger.error("can't map process instance {}", sourceAsString, e);
        }
      });

    ensureEveryRawDataInstanceContainsAllVariables(
      rawData, allInputVariablesWithBlankValue, allOutputVariablesWithNoValues
    );

    return createResult(rawData, searchHits.getTotalHits());
  }

  private void ensureEveryRawDataInstanceContainsAllVariables(final List<RawDataDecisionInstanceDto> rawData,
                                                              final Set<InputVariableEntry> inputVariables,
                                                              final Set<OutputVariableEntry> outputVariables) {
    rawData.forEach(data -> {
      inputVariables.forEach(
        inputVariableEntry -> data.getInputVariables().putIfAbsent(inputVariableEntry.getId(), inputVariableEntry)
      );
      outputVariables.forEach(
        outputVariableEntry -> data.getOutputVariables().putIfAbsent(outputVariableEntry.getId(), outputVariableEntry)
      );
    });
  }

  private Set<InputVariableEntry> getInputVariables(final DecisionInstanceDto processInstanceDto) {
    return processInstanceDto.getInputs().stream()
      .map(inputInstanceDto -> new InputVariableEntry(
        inputInstanceDto.getClauseId(),
        inputInstanceDto.getClauseName(),
        inputInstanceDto.getType(),
        ""
      ))
      .collect(Collectors.toSet());
  }

  private Set<OutputVariableEntry> getOutputVariables(final DecisionInstanceDto processInstanceDto) {
    return processInstanceDto.getOutputs().stream()
      .map(outputInstanceDto -> new OutputVariableEntry(
        outputInstanceDto.getClauseId(),
        outputInstanceDto.getClauseName(),
        outputInstanceDto.getType(),
        Collections.emptyList()
      ))
      .collect(Collectors.toSet());
  }

  private RawDataDecisionInstanceDto convertToRawDataEntry(final DecisionInstanceDto decisionInstanceDto) {
    RawDataDecisionInstanceDto rawDataInstance = new RawDataDecisionInstanceDto();
    rawDataInstance.setDecisionInstanceId(decisionInstanceDto.getDecisionInstanceId());
    rawDataInstance.setDecisionDefinitionId(decisionInstanceDto.getDecisionDefinitionId());
    rawDataInstance.setDecisionDefinitionKey(decisionInstanceDto.getDecisionDefinitionKey());
    rawDataInstance.setEvaluationDateTime(decisionInstanceDto.getEvaluationDateTime());
    rawDataInstance.setEngineName(decisionInstanceDto.getEngine());

    rawDataInstance.setInputVariables(
      decisionInstanceDto.getInputs().stream().collect(toMap(
        InputInstanceDto::getClauseId,
        this::mapToVariableEntry
      ))
    );

    rawDataInstance.setOutputVariables(
      decisionInstanceDto.getOutputs().stream().collect(toMap(
        OutputInstanceDto::getClauseId,
        this::mapToVariableEntry,
        (variableEntry, variableEntry2) -> {
          variableEntry.getValues().addAll(variableEntry2.getValues());
          return variableEntry;
        }
      ))
    );

    return rawDataInstance;
  }

  private InputVariableEntry mapToVariableEntry(final InputInstanceDto inputInstanceDto) {
    return new InputVariableEntry(
      inputInstanceDto.getClauseId(),
      inputInstanceDto.getClauseName(),
      inputInstanceDto.getType(),
      inputInstanceDto.getValue()
    );
  }

  private OutputVariableEntry mapToVariableEntry(final OutputInstanceDto outputInstanceDto) {
    return new OutputVariableEntry(
      outputInstanceDto.getClauseId(),
      outputInstanceDto.getClauseName(),
      outputInstanceDto.getType(),
      outputInstanceDto.getValue()
    );
  }

  private RawDataDecisionReportResultDto createResult(final List<RawDataDecisionInstanceDto> limitedRawDataResult,
                                                      final Long totalHits) {
    final RawDataDecisionReportResultDto result = new RawDataDecisionReportResultDto();
    result.setData(limitedRawDataResult);
    result.setComplete(limitedRawDataResult.size() == totalHits);
    result.setDecisionInstanceCount(totalHits);
    return result;
  }

}
