/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.export;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.COUNT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.FLOWNODE_DURATION_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto.Fields.businessKey;
import static io.camunda.optimize.service.db.report.interpreter.util.RawProcessDataResultDtoMapper.OBJECT_VARIABLE_VALUE_PLACEHOLDER;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.opencsv.CSVWriter;
import io.camunda.optimize.dto.optimize.FlowNodeTotalDurationDataDto;
import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataCountDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto.Fields;
import io.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;

public final class CSVUtils {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CSVUtils.class);
  private static final List<Fields> PROCESS_INSTANCE_FIELDS_TO_EXCLUDE =
      ImmutableList.of(businessKey);

  private CSVUtils() {}

  public static byte[] mapCsvLinesToCsvBytes(
      final List<String[]> csvStrings, final char csvDelimiter) {
    final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    final BufferedWriter bufferedWriter =
        new BufferedWriter(new OutputStreamWriter(arrayOutputStream));
    final CSVWriter csvWriter = new CSVWriter(bufferedWriter, csvDelimiter, '"', '"', "\r\n");

    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (final Exception e) {
      LOG.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

  public static <T extends IdResponseDto> List<String[]> mapIdList(final List<T> ids) {
    final List<String[]> result = new ArrayList<>();

    result.add(new String[] {"processInstanceId"});

    ids.forEach(idDto -> result.add(new String[] {idDto.getId()}));

    return result;
  }

  public static List<String[]> mapRawProcessReportInstances(
      final List<RawDataProcessInstanceDto> rawData,
      final Integer limit,
      final Integer offset,
      final TableColumnDto tableColumns,
      final boolean includeNewVariables) {
    final List<String[]> result = new ArrayList<>();
    final List<String> allCountKeys = extractAllPrefixedCountKeys();
    final List<String> allFlowNodeDurationKeys = extractAllPrefixedFlowNodeKeys(rawData);
    final List<String> allVariableKeys = extractAllPrefixedVariableKeys(rawData);
    // Ensure all fields are taken into account by tableColumns
    tableColumns.setIncludeNewVariables(includeNewVariables);
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());
    tableColumns.addCountColumns(allCountKeys);
    tableColumns.addNewAndRemoveUnexpectedFlowNodeDurationColumns(allFlowNodeDurationKeys);
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(allVariableKeys);
    final List<String> allIncludedKeysInOrder = tableColumns.getIncludedColumns();

    // header line
    result.add(allIncludedKeysInOrder.toArray(new String[0]));
    int currentPosition = 0;
    for (final RawDataProcessInstanceDto instanceDto : rawData) {
      final boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded)
          || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeysInOrder.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeysInOrder.get(i);
          final Optional<String> columnValue;
          if (allVariableKeys.contains(currentKey)) {
            columnValue = getVariableValue(instanceDto, currentKey);
            // if the current column is a flow node column
          } else if (allFlowNodeDurationKeys.contains(currentKey)) {
            columnValue = getFlowNodeDurationValue(instanceDto, currentKey);
          } else if (allCountKeys.contains(currentKey)) {
            columnValue = getCountValue(instanceDto, currentKey);
          } else {
            columnValue =
                getDtoFieldValue(instanceDto, RawDataProcessInstanceDto.class, currentKey);
          }
          dataLine[i] = columnValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }
    return result;
  }

  public static List<String[]> mapRawDecisionReportInstances(
      final List<RawDataDecisionInstanceDto> rawData,
      final Integer limit,
      final Integer offset,
      final TableColumnDto tableColumns) {
    final List<String[]> result = new ArrayList<>();
    final List<String> allVariableKeys = new ArrayList<>();
    final List<String> allInputVariableKeys = extractAllPrefixedDecisionInputKeys(rawData);
    final List<String> allOutputVariableKeys = extractAllPrefixedDecisionOutputKeys(rawData);
    allVariableKeys.addAll(allInputVariableKeys);
    allVariableKeys.addAll(allOutputVariableKeys);

    // Ensure all dto fields are taken into account by tableColumns
    tableColumns.addDtoColumns(extractAllDecisionInstanceDtoFieldKeys());

    // Ensure all variables are taken into account by tableColumns
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(allVariableKeys);
    final List<String> allIncludedKeysInOrder = tableColumns.getIncludedColumns();

    // header line
    result.add(allIncludedKeysInOrder.toArray(new String[0]));
    int currentPosition = 0;
    for (final RawDataDecisionInstanceDto instanceDto : rawData) {
      final boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded)
          || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeysInOrder.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeysInOrder.get(i);
          final Optional<String> optionalValue;
          if (allInputVariableKeys.contains(currentKey)) {
            optionalValue = getInputVariableValue(instanceDto, currentKey);
          } else if (allOutputVariableKeys.contains(currentKey)) {
            optionalValue = getOutputVariableValue(instanceDto, currentKey);
          } else {
            optionalValue =
                getDtoFieldValue(instanceDto, RawDataDecisionInstanceDto.class, currentKey);
          }
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  public static List<String[]> map(
      final List<MapResultEntryDto> values, final Integer limit, final Integer offset) {
    final List<String[]> result = new ArrayList<>();

    int currentPosition = 0;
    for (final MapResultEntryDto value : values) {
      final boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      final boolean offsetPassed = isOffsetPassed(offset, currentPosition);
      if ((offset == null && limitNotExceeded) || (offsetPassed && limitNotExceeded)) {
        final String[] line = new String[2];
        line[0] = value.getLabel();
        line[1] = Optional.ofNullable(value.getValue()).map(Object::toString).orElse("");
        result.add(line);
      }
      currentPosition = currentPosition + 1;
    }
    return result;
  }

  public static String mapAggregationType(final AggregationDto aggregationDto) {
    switch (aggregationDto.getType()) {
      case AVERAGE:
        return "average";
      case MIN:
        return "minimum";
      case MAX:
        return "maximum";
      case SUM:
        return "sum";
      case PERCENTILE:
        return "p" + aggregationDto.getValue();
      default:
        throw new IllegalStateException("Uncovered type: " + aggregationDto.getValue());
    }
  }

  public static List<String> extractAllDecisionInstanceDtoFieldKeys() {
    return Arrays.stream(RawDataDecisionInstanceDto.Fields.values())
        .map(RawDataDecisionInstanceDto.Fields::name)
        .collect(toList());
  }

  public static List<String> extractAllProcessInstanceDtoFieldKeys() {
    return Arrays.stream(RawDataProcessInstanceDto.Fields.values())
        .filter(field -> !PROCESS_INSTANCE_FIELDS_TO_EXCLUDE.contains(field))
        .map(RawDataProcessInstanceDto.Fields::name)
        .collect(toList());
  }

  private static String stripOffPrefix(final String currentKey, final String prefix) {
    return currentKey.replace(prefix, "");
  }

  private static List<String> extractAllPrefixedVariableKeys(
      final List<RawDataProcessInstanceDto> rawData) {
    final Set<String> variableKeys = new HashSet<>();
    for (final RawDataProcessInstanceDto pi : rawData) {
      final Map<String, Object> variables = pi.getVariables();
      if (variables != null) {
        variables.entrySet().stream()
            .filter(entry -> !OBJECT_VARIABLE_VALUE_PLACEHOLDER.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .forEach(variableKeys::add);
      }
    }
    return variableKeys.stream().map(key -> VARIABLE_PREFIX + key).collect(toList());
  }

  public static List<String> extractAllPrefixedFlowNodeKeys(
      final List<RawDataProcessInstanceDto> rawData) {
    final List<String> flowNodeKeys = new ArrayList<>();
    for (final RawDataProcessInstanceDto currentInstanceDataDto : rawData) {
      final Optional<Map<String, FlowNodeTotalDurationDataDto>> flowNodeDurations =
          Optional.ofNullable(currentInstanceDataDto.getFlowNodeDurations());
      flowNodeDurations.ifPresent(
          stringFlowNodeTotalDurationDataDtoMap ->
              stringFlowNodeTotalDurationDataDtoMap.keySet().stream()
                  // prefixing all flownode columns with "dur:"
                  .map(
                      flowNodeTotalDurationDataDto ->
                          FLOWNODE_DURATION_PREFIX + flowNodeTotalDurationDataDto)
                  .forEach(flowNodeKeys::add));
    }
    return flowNodeKeys;
  }

  public static List<String> extractAllPrefixedCountKeys() {
    return List.of(
        addCountPrefix(RawDataCountDto.Fields.incidents),
        addCountPrefix(RawDataCountDto.Fields.openIncidents),
        addCountPrefix(RawDataCountDto.Fields.userTasks));
  }

  private static List<String> extractAllPrefixedDecisionInputKeys(
      final List<RawDataDecisionInstanceDto> rawData) {
    final Set<String> inputKeys = new HashSet<>();
    for (final RawDataDecisionInstanceDto pi : rawData) {
      if (pi.getInputVariables() != null) {
        inputKeys.addAll(pi.getInputVariables().keySet());
      }
    }
    return inputKeys.stream().map(key -> INPUT_PREFIX + key).collect(toList());
  }

  private static List<String> extractAllPrefixedDecisionOutputKeys(
      final List<RawDataDecisionInstanceDto> rawData) {
    final Set<String> outputKeys = new HashSet<>();
    for (final RawDataDecisionInstanceDto di : rawData) {
      if (di.getOutputVariables() != null) {
        outputKeys.addAll(di.getOutputVariables().keySet());
      }
    }
    return outputKeys.stream().map(key -> OUTPUT_PREFIX + key).collect(toList());
  }

  private static <T> Optional<String> getDtoFieldValue(
      final T instanceDto, final Class<T> instanceClass, final String fieldKey) {
    try {
      return Optional.of(new PropertyDescriptor(fieldKey, instanceClass))
          .map(
              descriptor -> {
                Optional<Object> value = Optional.empty();
                try {
                  value = Optional.ofNullable(descriptor.getReadMethod().invoke(instanceDto));
                } catch (final Exception e) {
                  LOG.error("can't read value of field", e);
                }
                return value.map(Object::toString).orElse(null);
              });
    } catch (final IntrospectionException e) {
      // no field like that
      LOG.error(
          "Tried to access RawDataInstanceDto field that did not exist {} on class {}",
          fieldKey,
          instanceClass);
      return Optional.empty();
    }
  }

  private static Optional<String> getVariableValue(
      final RawDataProcessInstanceDto instanceDto, final String variableKey) {
    return Optional.ofNullable(instanceDto.getVariables())
        .map(variables -> variables.get(stripOffPrefix(variableKey, VARIABLE_PREFIX)))
        .filter(variable -> !OBJECT_VARIABLE_VALUE_PLACEHOLDER.equals(variable))
        .map(Object::toString);
  }

  private static Optional<String> getFlowNodeDurationValue(
      final RawDataProcessInstanceDto instanceDto, String flowNodeKey) {
    flowNodeKey = flowNodeKey.replace(FLOWNODE_DURATION_PREFIX, "");
    if (instanceDto.getFlowNodeDurations().containsKey(flowNodeKey)) {
      return Optional.of(
          (Long.toString(instanceDto.getFlowNodeDurations().get(flowNodeKey).getValue())));
    } else {
      return Optional.empty();
    }
  }

  private static Optional<String> getCountValue(
      final RawDataProcessInstanceDto instanceDto, final String flowNodeKey) {
    if (flowNodeKey.equals(addCountPrefix(RawDataCountDto.Fields.userTasks))) {
      return Optional.of(Long.toString(instanceDto.getCounts().getUserTasks()));
    } else if (flowNodeKey.equals(addCountPrefix(RawDataCountDto.Fields.incidents))) {
      return Optional.of(Long.toString(instanceDto.getCounts().getIncidents()));
    } else if (flowNodeKey.equals(addCountPrefix(RawDataCountDto.Fields.openIncidents))) {
      return Optional.of(Long.toString(instanceDto.getCounts().getOpenIncidents()));
    } else {
      return Optional.empty();
    }
  }

  private static String addCountPrefix(final RawDataCountDto.Fields openIncidents) {
    return COUNT_PREFIX + openIncidents;
  }

  private static Optional<String> getOutputVariableValue(
      final RawDataDecisionInstanceDto instanceDto, final String inputKey) {
    return Optional.ofNullable(instanceDto.getOutputVariables())
        .map(outputs -> outputs.get(stripOffPrefix(inputKey, OUTPUT_PREFIX)))
        .map(OutputVariableEntry::getValues)
        .map(values -> values.stream().map(Object::toString).collect(joining(",")));
  }

  private static Optional<String> getInputVariableValue(
      final RawDataDecisionInstanceDto instanceDto, final String outputKey) {
    return Optional.ofNullable(instanceDto.getInputVariables())
        .map(inputs -> inputs.get(stripOffPrefix(outputKey, INPUT_PREFIX)))
        .map(InputVariableEntry::getValue)
        .map(Object::toString);
  }

  private static boolean isOffsetPassed(final Integer offset, final int currentPosition) {
    return offset != null && currentPosition >= offset;
  }

  private static boolean isLimitNotExceeded(final Integer limit, final List<String[]> result) {
    return limit == null || result.size() <= limit;
  }
}
