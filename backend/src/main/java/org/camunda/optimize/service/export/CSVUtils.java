package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.single.result.raw.RawDataProcessInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class CSVUtils {
  private static final String MAP = "java.util.Map";
  private static final String VARIABLE_PREFIX = "variable:";
  private static Logger logger = LoggerFactory.getLogger(CSVUtils.class);

  public static List<String[]> map(List<RawDataProcessInstanceDto> rawData, Integer limit, Integer offset) {
    return map(rawData, limit, offset, Collections.emptySet());
  }

  public static List<String[]> map(List<RawDataProcessInstanceDto> rawData,
                                   Integer limit,
                                   Integer offset,
                                   Set<String> excludedColumns) {
    final List<String[]> result = new ArrayList<>();

    // column names contain prefixes that must be stripped off to get the plain keys needed for exclusion
    final Set<String> excludedKeys = stripOffVariablePrefixes(excludedColumns);

    final List<String> includedDtoFields = extractAllDtoFieldKeys(RawDataProcessInstanceDto.class);
    includedDtoFields.removeAll(excludedKeys);
    final List<String> includedVariableKeys = extractAllVariableKeys(rawData);
    includedVariableKeys.removeAll(excludedKeys);

    final List<String> allIncludedKeys = union(includedDtoFields, includedVariableKeys);
    final String[] headerLine = constructHeaderLine(includedDtoFields, includedVariableKeys);
    result.add(headerLine);

    int currentPosition = 0;
    for (RawDataProcessInstanceDto pi : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      if ((offset == null && limitNotExceeded) || (isOffsetPassed(offset, currentPosition) && limitNotExceeded)) {
        final String[] dataLine = new String[allIncludedKeys.size()];
        for (int i = 0; i < dataLine.length; i++) {
          final String currentKey = allIncludedKeys.get(i);
          final Optional<String> optionalValue = includedVariableKeys.contains(currentKey)
            ? getDataValueForVariableKey(currentKey, pi)
            : getDataValueForDtoFieldKey(currentKey, pi);
          dataLine[i] = optionalValue.orElse(null);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    return result;
  }

  private static Set<String> stripOffVariablePrefixes(Set<String> excludedColumns) {
    return excludedColumns.stream()
      .map(columnName -> columnName.replace(VARIABLE_PREFIX, ""))
      .collect(Collectors.toSet());
  }

  private static List<String> union(List<String> list1, List<String> list2) {
    final List<String> unionList = new ArrayList<>(list1);
    unionList.addAll(list2);
    return unionList;
  }

  private static List<String> extractAllDtoFieldKeys(Class<RawDataProcessInstanceDto> dtoClass) {
    final List<String> fieldKeys = new ArrayList<>();
    for (Field f : dtoClass.getDeclaredFields()) {
      if (!MAP.equals(f.getType().getName())) {
        fieldKeys.add(f.getName());
      }
    }
    return fieldKeys;
  }

  private static List<String> extractAllVariableKeys(List<RawDataProcessInstanceDto> rawData) {
    Set<String> variableKeys = new HashSet<>();
    for (RawDataProcessInstanceDto pi : rawData) {
      if (pi.getVariables() != null) {
        variableKeys.addAll(pi.getVariables().keySet());
      }
    }
    return new ArrayList<>(variableKeys);
  }

  public static boolean isOffsetPassed(Integer offset, int currentPosition) {
    return offset != null && currentPosition >= offset;
  }

  private static Optional<String> getDataValueForVariableKey(String key, RawDataProcessInstanceDto pi) {
    return Optional.ofNullable(pi.getVariables())
      .map(variables -> variables.get(key))
      .map(Object::toString);
  }

  private static Optional<String> getDataValueForDtoFieldKey(String key, RawDataProcessInstanceDto pi) {
    try {
      return Optional.of(new PropertyDescriptor(key, RawDataProcessInstanceDto.class))
        .map((descriptor) -> {
          Optional<Object> piValue = Optional.empty();
          try {
            piValue = Optional.ofNullable(descriptor.getReadMethod().invoke(pi));
          } catch (Exception e) {
            logger.error("can't read value of field", e);
          }
          return piValue.map(Object::toString).orElse(null);
        });
    } catch (IntrospectionException e) {
      // no field like that
      logger.error("Tried to access RawDataProcessInstanceDto field that did not exist {}", key);
      return Optional.empty();
    }
  }

  private static String[] constructHeaderLine(List<String> fieldKeys, List<String> variableKeys) {
    List<String> headerLine = new ArrayList<>(fieldKeys);

    variableKeys.stream().map(key -> VARIABLE_PREFIX + key).forEach(headerLine::add);

    return headerLine.toArray(new String[0]);
  }

  public static List<String[]> map(Map<String, Long> valuesMap, Integer limit, Integer offset) {
    List<String[]> result = new ArrayList<>();

    int currentPosition = 0;
    for (Map.Entry<String, Long> value : valuesMap.entrySet()) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      boolean offsetPassed = isOffsetPassed(offset, currentPosition);
      if ((offset == null && limitNotExceeded) || (offsetPassed && limitNotExceeded)) {
        String[] line = new String[2];
        line[0] = value.getKey();
        line[1] = value.getValue().toString();
        result.add(line);
      }
      currentPosition = currentPosition + 1;
    }
    return result;
  }

  public static boolean isLimitNotExceeded(Integer limit, List<String[]> result) {
    return limit == null || result.size() <= limit;
  }

  public static List<String[]> map(List<RawDataProcessInstanceDto> toMap) {
    return CSVUtils.map(toMap, null, null);
  }
}
