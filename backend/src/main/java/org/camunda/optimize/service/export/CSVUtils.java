/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.export;

import com.opencsv.CSVWriter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;

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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.VARIABLE_PREFIX;
import static org.camunda.optimize.service.es.report.command.process.mapping.RawProcessDataResultDtoMapper.OBJECT_VARIABLE_VALUE_PLACEHOLDER;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CSVUtils {

  public static byte[] mapCsvLinesToCsvBytes(final List<String[]> csvStrings, final char csvDelimiter) {
    final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
    final BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(arrayOutputStream));
    final CSVWriter csvWriter = new CSVWriter(bufferedWriter, csvDelimiter, '"', '"', "\r\n");

    byte[] bytes = null;
    try {
      csvWriter.writeAll(csvStrings);
      bufferedWriter.flush();
      bufferedWriter.close();
      arrayOutputStream.flush();
      bytes = arrayOutputStream.toByteArray();
      arrayOutputStream.close();
    } catch (Exception e) {
      log.error("can't write CSV to buffer", e);
    }
    return bytes;
  }

  public static <T extends IdResponseDto> List<String[]> mapIdList(final List<T> ids) {
    final List<String[]> result = new ArrayList<>();

    result.add(new String[]{"processInstanceId"});

    ids.forEach(idDto -> result.add(new String[]{idDto.getId()}));

    return result;
  }

  public static List<String[]> mapRawProcessReportInstances(final List<RawDataProcessInstanceDto> rawData,
                                                            final Integer limit,
                                                            final Integer offset,
                                                            final TableColumnDto tableColumns) {
    final List<String[]> result = new ArrayList<>();
    final List<String> allVariableKeys = extractAllPrefixedVariableKeys(rawData);

    // Ensure all dto fields are taken into account by tableColumns
    tableColumns.addDtoColumns(extractAllProcessInstanceDtoFieldKeys());

    // Ensure all variables are taken into account by tableColumns
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(allVariableKeys);
    final List<String> allIncludedKeysInOrder = tableColumns.getIncludedColumns();

    // header line
    result.add(allIncludedKeysInOrder.toArray(new String[0]));

    int currentPosition = 0;
    for (RawDataProcessInstanceDto instanceDto : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded) || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeysInOrder.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeysInOrder.get(i);
          final Optional<String> optionalValue = allVariableKeys.contains(currentKey)
            ? getVariableValue(instanceDto, currentKey)
            : getDtoFieldValue(instanceDto, RawDataProcessInstanceDto.class, currentKey);
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  public static List<String[]> mapRawDecisionReportInstances(final List<RawDataDecisionInstanceDto> rawData,
                                                             final Integer limit,
                                                             final Integer offset,
                                                             final TableColumnDto tableColumns) {
    final List<String[]> result = new ArrayList<>();
    List<String> allVariableKeys = new ArrayList<>();
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
    for (RawDataDecisionInstanceDto instanceDto : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded) || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeysInOrder.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeysInOrder.get(i);
          final Optional<String> optionalValue;
          if (allInputVariableKeys.contains(currentKey)) {
            optionalValue = getInputVariableValue(instanceDto, currentKey);
          } else if (allOutputVariableKeys.contains(currentKey)) {
            optionalValue = getOutputVariableValue(instanceDto, currentKey);
          } else {
            optionalValue = getDtoFieldValue(instanceDto, RawDataDecisionInstanceDto.class, currentKey);
          }
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  public static List<String[]> map(List<MapResultEntryDto> values, Integer limit, Integer offset) {
    List<String[]> result = new ArrayList<>();

    int currentPosition = 0;
    for (MapResultEntryDto value : values) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      boolean offsetPassed = isOffsetPassed(offset, currentPosition);
      if ((offset == null && limitNotExceeded) || (offsetPassed && limitNotExceeded)) {
        String[] line = new String[2];
        line[0] = value.getLabel();
        line[1] = Optional.ofNullable(value.getValue()).map(Object::toString).orElse("");
        result.add(line);
      }
      currentPosition = currentPosition + 1;
    }
    return result;
  }

  public static String mapAggregationType(AggregationDto aggregationDto) {
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
      .map(RawDataProcessInstanceDto.Fields::name)
      .collect(toList());
  }

  private static String stripOffPrefix(final String currentKey, final String prefix) {
    return currentKey.replace(prefix, "");
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private static List<String> extractAllPrefixedVariableKeys(List<RawDataProcessInstanceDto> rawData) {
    Set<String> variableKeys = new HashSet<>();
    for (RawDataProcessInstanceDto pi : rawData) {
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

  private static List<String> extractAllPrefixedDecisionInputKeys(List<RawDataDecisionInstanceDto> rawData) {
    Set<String> inputKeys = new HashSet<>();
    for (RawDataDecisionInstanceDto pi : rawData) {
      if (pi.getInputVariables() != null) {
        inputKeys.addAll(pi.getInputVariables().keySet());
      }
    }
    return inputKeys.stream().map(key -> INPUT_PREFIX + key).collect(toList());
  }

  private static List<String> extractAllPrefixedDecisionOutputKeys(List<RawDataDecisionInstanceDto> rawData) {
    Set<String> outputKeys = new HashSet<>();
    for (RawDataDecisionInstanceDto di : rawData) {
      if (di.getOutputVariables() != null) {
        outputKeys.addAll(di.getOutputVariables().keySet());
      }
    }
    return outputKeys.stream().map(key -> OUTPUT_PREFIX + key).collect(toList());
  }

  private static <T> Optional<String> getDtoFieldValue(final T instanceDto,
                                                       final Class<T> instanceClass,
                                                       final String fieldKey) {
    try {
      return Optional.of(new PropertyDescriptor(fieldKey, instanceClass))
        .map(descriptor -> {
          Optional<Object> value = Optional.empty();
          try {
            value = Optional.ofNullable(descriptor.getReadMethod().invoke(instanceDto));
          } catch (Exception e) {
            log.error("can't read value of field", e);
          }
          return value.map(Object::toString).orElse(null);
        });
    } catch (IntrospectionException e) {
      // no field like that
      log.error(
        "Tried to access RawDataInstanceDto field that did not exist {} on class {}",
        fieldKey,
        instanceClass
      );
      return Optional.empty();
    }
  }

  private static Optional<String> getVariableValue(final RawDataProcessInstanceDto instanceDto, String variableKey) {
    return Optional.ofNullable(instanceDto.getVariables())
      .map(variables -> variables.get(stripOffPrefix(variableKey, VARIABLE_PREFIX)))
      .filter(variable -> !OBJECT_VARIABLE_VALUE_PLACEHOLDER.equals(variable))
      .map(Object::toString);
  }

  private static Optional<String> getOutputVariableValue(final RawDataDecisionInstanceDto instanceDto,
                                                         final String inputKey) {
    return Optional.ofNullable(instanceDto.getOutputVariables())
      .map(outputs -> outputs.get(stripOffPrefix(inputKey, OUTPUT_PREFIX)))
      .map(OutputVariableEntry::getValues)
      .map(values -> values.stream().map(Object::toString).collect(joining(",")));
  }

  private static Optional<String> getInputVariableValue(final RawDataDecisionInstanceDto instanceDto,
                                                        final String outputKey) {
    return Optional.ofNullable(instanceDto.getInputVariables())
      .map(inputs -> inputs.get(stripOffPrefix(outputKey, INPUT_PREFIX)))
      .map(InputVariableEntry::getValue)
      .map(Object::toString);
  }

  private static boolean isOffsetPassed(Integer offset, int currentPosition) {
    return offset != null && currentPosition >= offset;
  }

  private static boolean isLimitNotExceeded(Integer limit, List<String[]> result) {
    return limit == null || result.size() <= limit;
  }

}
