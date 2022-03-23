/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.sisyphsu.dateparser.DateParserUtils;
import com.github.wnameless.json.base.JacksonJsonCore;
import com.github.wnameless.json.flattener.FlattenMode;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.flattener.JsonifyArrayList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableUpdateDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.BOOLEAN;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.DOUBLE;
import static org.camunda.optimize.dto.optimize.query.variable.VariableType.OBJECT;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_SERIALIZATION_DATA_FORMAT;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_TYPE_JSON;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Component
@Slf4j
public class ObjectVariableService {

  private static final String LIST_SIZE_VARIABLE_SUFFIX = "_listSize";
  private static final DateTimeFormatter OPTIMIZE_DATE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private final ObjectMapper objectMapper;

  public ObjectVariableService() {
    this.objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  public List<ProcessVariableDto> convertObjectVariablesForImport(final List<ProcessVariableUpdateDto> variables) {
    List<ProcessVariableDto> resultList = new ArrayList<>();
    for (ProcessVariableUpdateDto variableUpdateDto : variables) {
      if (isNonNullNativeJsonVariable(variableUpdateDto) || isNonNullObjectVariable(variableUpdateDto)) {
        if (isSupportedSerializationFormat(variableUpdateDto)) {
          flattenJsonObjectVariableAndAddToResult(variableUpdateDto, resultList);
          formatJsonObjectVariableAndAddToResult(variableUpdateDto, resultList);
        }
      } else {
        resultList.add(
          createSkeletonVariableDto(variableUpdateDto)
            .setId(variableUpdateDto.getId())
            .setName(variableUpdateDto.getName())
            .setType(variableUpdateDto.getType())
            .setValue(Collections.singletonList(variableUpdateDto.getValue()))
        );
      }
    }
    return resultList;
  }

  private void formatJsonObjectVariableAndAddToResult(final ProcessVariableUpdateDto variableUpdate,
                                                      final List<ProcessVariableDto> resultList) {
    try {
      final Object jsonObject = objectMapper.readValue(variableUpdate.getValue(), Object.class);
      if (isPrimitiveOrListOfPrimitives(jsonObject)) {
        // nothing to do as a "flattened" string/number/bool variable is the same as the raw object variable
        return;
      }
      resultList.add(
        createSkeletonVariableDto(variableUpdate)
          .setId(variableUpdate.getId())
          .setName(variableUpdate.getName())
          .setType(OBJECT.getId())
          .setValue(Collections.singletonList(objectMapper.writeValueAsString(jsonObject)))
      );
    } catch (JsonProcessingException e) {
      log.error("Error while formatting json object variable with name '{}'.", variableUpdate.getName(), e);
    }
  }

  private boolean isPrimitiveOrListOfPrimitives(final Object jsonObject) {
    boolean isListOfPrimitives = false;
    if (jsonObject instanceof ArrayList && !((ArrayList<?>) jsonObject).isEmpty()) {
      @SuppressWarnings(UNCHECKED_CAST) final Object firstItem = ((ArrayList<Object>) jsonObject).get(0);
      isListOfPrimitives = firstItem instanceof String || firstItem instanceof Number || firstItem instanceof Boolean;
    }
    return jsonObject instanceof String || jsonObject instanceof Number
      || jsonObject instanceof Boolean || isListOfPrimitives;
  }

  private void flattenJsonObjectVariableAndAddToResult(final ProcessVariableUpdateDto variable,
                                                       final List<ProcessVariableDto> resultList) {
    try {
      new JsonFlattener(new JacksonJsonCore(objectMapper), variable.getValue())
        .withFlattenMode(FlattenMode.KEEP_ARRAYS)
        .flattenAsMap()
        .entrySet()
        .stream()
        .map(e -> mapToFlattenedVariable(e.getKey(), e.getValue(), variable))
        .forEach(resultList::addAll);
    } catch (Exception exception) {
      log.error(
        "Error while flattening json object variable with name '{}'.",
        variable.getName(),
        exception
      );
    }
  }

  private List<ProcessVariableDto> mapToFlattenedVariable(final String name, final Object value,
                                                          final ProcessVariableUpdateDto origin) {
    if (value == null || String.valueOf(value).isEmpty()) {
      log.info("Variable attribute '{}' of '{}' is null or empty and won't be imported", name, origin.getName());
      return Collections.emptyList();
    }

    List<ProcessVariableDto> resultList = new ArrayList<>();
    ProcessVariableDto newVariable = createSkeletonVariableDto(origin);
    addNameToSkeletonVariable(name, newVariable, origin);

    if (value instanceof JsonifyArrayList) {
      newVariable.setName(String.join(".", newVariable.getName(), LIST_SIZE_VARIABLE_SUFFIX));
      newVariable.setType(VariableType.LONG.getId());
      newVariable.setValue(Collections.singletonList(String.valueOf(((JsonifyArrayList<?>) value).size())));
      addVariableForListProperty(name, value, origin, resultList);
    } else if (value instanceof String) {
      parseStringOrDateVariable(value, Collections.singletonList(value), newVariable);
    } else if (value instanceof Boolean) {
      parseBooleanVariable(Collections.singletonList(value), newVariable);
    } else if (value instanceof Number) {
      parseNumberVariable(Collections.singletonList(value), newVariable);
    } else {
      log.warn(
        "Variable attribute '{}' of '{}' with type {} and value '{}' is not supported and won't be imported.",
        name, origin.getName(), value.getClass().getSimpleName(), value
      );
      return Collections.emptyList();
    }
    addIdToSkeletonVariable(name, newVariable, origin);
    resultList.add(newVariable);
    return resultList;
  }

  private void addVariableForListProperty(final String name, final Object value, final ProcessVariableUpdateDto origin,
                                          final List<ProcessVariableDto> resultList) {
    @SuppressWarnings(UNCHECKED_CAST) final ArrayList<Object> originList = (ArrayList<Object>) value;
    if (originList.isEmpty()) {
      return;
    }
    final ProcessVariableDto newListVar = createSkeletonVariableDto(origin);
    addNameToSkeletonVariable(name, newListVar, origin);
    addIdToSkeletonVariable(name, newListVar, origin);

    final Object firstItem = originList.get(0);
    if (firstItem instanceof String) {
      parseStringOrDateVariable(firstItem, originList, newListVar);
    } else if (firstItem instanceof Boolean) {
      parseBooleanVariable(originList, newListVar);
    } else if (firstItem instanceof Number) {
      parseNumberVariable(originList, newListVar);
    } else {
      log.warn(
        "List variable attribute '{}' of '{}' with type {} and value '{}' is not supported and won't be imported.",
        name, origin.getName(), firstItem.getClass().getSimpleName(), value
      );
    }
    resultList.add(newListVar);
  }

  private ProcessVariableDto createSkeletonVariableDto(final ProcessVariableUpdateDto origin) {
    return new ProcessVariableDto()
      .setEngineAlias(origin.getEngineAlias())
      .setProcessDefinitionId(origin.getProcessDefinitionId())
      .setProcessDefinitionKey(origin.getProcessDefinitionKey())
      .setProcessInstanceId(origin.getProcessInstanceId())
      .setVersion(origin.getVersion())
      .setTimestamp(origin.getTimestamp())
      .setTenantId(origin.getTenantId());
  }

  private void addNameToSkeletonVariable(final String propertyName, final ProcessVariableDto newVariable,
                                         final ProcessVariableUpdateDto origin) {
    if (JsonFlattener.ROOT.equals(propertyName)) {
      // if variable is just a string, number, or list keep original name
      newVariable.setName(origin.getName());
    } else {
      newVariable.setName(String.join(".", origin.getName(), propertyName));
    }
  }

  private void addIdToSkeletonVariable(final String propertyName, final ProcessVariableDto newVariable,
                                       final ProcessVariableUpdateDto origin) {
    if (JsonFlattener.ROOT.equals(propertyName) && !(newVariable.getName().contains(LIST_SIZE_VARIABLE_SUFFIX))) {
      // if variable is just a string, number, or list keep original ID
      newVariable.setId(origin.getId());
    } else {
      // the ID  needs to be unique for each new variable instance but consistent so that version updates get overridden
      newVariable.setId(origin.getId() + "_" + newVariable.getName());
    }
  }

  private Optional<OffsetDateTime> parsePossibleDate(final String dateAsString) {
    try {
      return Optional.of(DateParserUtils.parseOffsetDateTime(dateAsString));
    } catch (Exception e) {
      return Optional.empty();
    }
  }

  private void parseStringOrDateVariable(final Object firstValue, final List<Object> valueList,
                                         final ProcessVariableDto newVariable) {
    final Optional<OffsetDateTime> optDate = parsePossibleDate(String.valueOf(firstValue));
    if (optDate.isPresent()) {
      newVariable.setType(VariableType.DATE.getId());
      newVariable.setValue(valueList.stream()
                             .map(item -> parsePossibleDate(String.valueOf(item)).orElse(null))
                             .filter(Objects::nonNull)
                             .map(OPTIMIZE_DATE_TIME_FORMATTER::format)
                             .collect(toList()));
    } else {
      newVariable.setType(VariableType.STRING.getId());
      newVariable.setValue(valueList.stream().map(String::valueOf).collect(toList()));
    }
  }

  private void parseBooleanVariable(final List<Object> valueList,
                                    final ProcessVariableDto newVariable) {
    newVariable.setType(BOOLEAN.getId());
    newVariable.setValue(valueList.stream().map(item -> ((Boolean) item).toString()).collect(toList()));
  }

  private void parseNumberVariable(final List<Object> valueList,
                                   final ProcessVariableDto newVariable) {
    newVariable.setType(DOUBLE.getId());
    newVariable.setValue(valueList.stream()
                           .map(item -> String.valueOf(((Number) item).doubleValue()))
                           .collect(toList()));
  }

  private boolean isNonNullNativeJsonVariable(final ProcessVariableUpdateDto originVariable) {
    return originVariable.getValue() != null && VARIABLE_TYPE_JSON.equalsIgnoreCase(originVariable.getType());
  }

  private boolean isNonNullObjectVariable(final ProcessVariableUpdateDto originVariable) {
    return originVariable.getValue() != null && VARIABLE_TYPE_OBJECT.equalsIgnoreCase(originVariable.getType());
  }

  private boolean isSupportedSerializationFormat(final ProcessVariableUpdateDto originVariable) {
    if (isNonNullNativeJsonVariable(originVariable)) {
      return true; // serializationFormat is irrelevant for native JSON variables
    } else {
      final Optional<String> serializationDataFormat =
        Optional.ofNullable(String.valueOf(originVariable.getValueInfo().get(VARIABLE_SERIALIZATION_DATA_FORMAT)));
      if (serializationDataFormat.stream().anyMatch(APPLICATION_JSON::equals)) {
        return true;
      } else {
        log.warn("Object variable '{}' will not be imported due to unsupported serializationDataFormat: {}. " +
                   "Object variables must have serializationDataFormat application/json.",
                 originVariable.getName(), serializationDataFormat.orElse("no format specified")
        );
        return false;
      }
    }
  }
}
