package org.camunda.optimize.service.export;

import org.camunda.optimize.dto.optimize.query.report.result.raw.RawDataProcessInstanceDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author Askar Akhmerov
 */
public class CSVUtils {
  private static final String MAP = "java.util.Map";
  private static final String VARIABLE_PREFIX = "variable:";
  private static Logger logger = LoggerFactory.getLogger(CSVUtils.class);

  public static List<String[]> map(List<RawDataProcessInstanceDto> rawData, Integer limit, Integer offset) {
    List<String[]> result = new ArrayList<>();

    Set<String> variableKeys = new HashSet<>();
    for (RawDataProcessInstanceDto pi : rawData) {
      if (pi.getVariables() != null) {
        variableKeys.addAll(pi.getVariables().keySet());
      }
    }

    String[] headerLine = constructHeaderLine(variableKeys);
    result.add(headerLine);

    int currentPosition = 0;
    for (RawDataProcessInstanceDto pi : rawData) {
      boolean limitNotExceeded = isLimitNotExceeded(limit, result);
      boolean offsetPassed = isOffsetPassed(offset, currentPosition);
      if ((offset == null && limitNotExceeded) || (offsetPassed && limitNotExceeded)) {
        String[] dataLine = newEmptyDataLine(variableKeys);
        for (int i = 0; i < dataLine.length; i++) {
          dataLine[i] = getDataValueForHeader(headerLine[i], pi);
        }
        result.add(dataLine);
      }
      currentPosition = currentPosition + 1;
    }

    for (int i = 0; i < result.get(0).length; i++) {
      if (variableKeys.contains(result.get(0)[i])) {
        result.get(0)[i] = VARIABLE_PREFIX + result.get(0)[i];
      }
    }

    return result;
  }

  public static boolean isOffsetPassed(Integer offset, int currentPosition) {
    return offset != null && currentPosition >= offset;
  }

  private static String getDataValueForHeader(String headerName, RawDataProcessInstanceDto pi) {
    String name = headerName;
    Optional<PropertyDescriptor> propertyDescriptor = Optional.empty();
    try {
      propertyDescriptor = Optional.of(new PropertyDescriptor(name, RawDataProcessInstanceDto.class));
    } catch (IntrospectionException e) {
      //not bad, there is no field like that, so it's a variable
    }

    String variableValue = null;
    if (pi.getVariables() != null) {
      variableValue = Optional.ofNullable(pi.getVariables().get(name))
          .map(Object::toString)
          .orElse(null);
    }
    String dataValue = propertyDescriptor
        .map((descriptor) -> {
          Optional<Object> piValue = Optional.empty();
          try {
            piValue = Optional.ofNullable(descriptor.getReadMethod().invoke(pi));
          } catch (Exception e) {
            logger.error("can't read value of field", e);
          }
          return piValue.map((fieldValue) -> fieldValue.toString())
              .orElse(null);
        })
        .orElse(variableValue);

    return dataValue;
  }

  private static String[] constructHeaderLine(Set<String> variableKeys) {
    String[] headerLine = newEmptyHeaderLine(variableKeys);
    int i = 0;
    for (Field f : RawDataProcessInstanceDto.class.getDeclaredFields()) {
      if (!MAP.equals(f.getType().getName())) {
        headerLine[i] = f.getName();
        i = i + 1;
      }
    }
    for (String varName : variableKeys) {
      headerLine[i] = varName;
      i = i + 1;
    }
    return headerLine;
  }

  private static String[] newEmptyHeaderLine(Set<String> variableKeys) {
    //one less then all fields due to variables map
    int sizeWithoutMap = RawDataProcessInstanceDto.class.getDeclaredFields().length - 1;
    return new String[sizeWithoutMap + variableKeys.size()];
  }

  private static String[] newEmptyDataLine(Set<String> variableKeys) {
    //one less then all fields due to variables map
    int sizeWithoutMap = RawDataProcessInstanceDto.class.getDeclaredFields().length - 1;
    return new String[sizeWithoutMap + variableKeys.size()];
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
    return limit == null || (limit != null && result.size() <= limit);
  }

  public static List<String[]> map(List<RawDataProcessInstanceDto> toMap) {
    return CSVUtils.map(toMap, null, null);
  }
}
