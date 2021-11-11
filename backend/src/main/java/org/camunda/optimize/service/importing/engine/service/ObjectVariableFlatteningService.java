/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sisyphsu.dateparser.DateParserUtils;
import com.github.wnameless.json.base.JacksonJsonCore;
import com.github.wnameless.json.flattener.FlattenMode;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.flattener.JsonifyArrayList;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_SERIALIZATION_DATA_FORMAT;
import static org.camunda.optimize.service.util.importing.EngineConstants.VARIABLE_TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
@Slf4j
public class ObjectVariableFlatteningService {

  private static final int VALUE_MAX_LENGTH = 4000;
  private static final String LIST_SIZE_VARIABLE_SUFFIX = "_listSize";
  private static final DateTimeFormatter OPTIMIZE_DATE_TIME_FORMATTER =
    DateTimeFormatter.ofPattern(OPTIMIZE_DATE_FORMAT);

  private final ObjectMapper objectMapper;

  public ObjectVariableFlatteningService() {
    this.objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    objectMapper.configure(DeserializationFeature.USE_LONG_FOR_INTS, true);
  }

  public List<PluginVariableDto> flattenObjectVariables(final List<PluginVariableDto> variables) {
    List<PluginVariableDto> resultList = new ArrayList<>();
    for (PluginVariableDto pluginVariableDto : variables) {
      if (VARIABLE_TYPE_OBJECT.equalsIgnoreCase(pluginVariableDto.getType())) {
        final Optional<String> serializationDataFormat =
          Optional.ofNullable(String.valueOf(pluginVariableDto.getValueInfo().get(VARIABLE_SERIALIZATION_DATA_FORMAT)));
        if (serializationDataFormat.stream().anyMatch(APPLICATION_JSON::equals)) {
          flattenJsonObjectVariable(pluginVariableDto, resultList);
        } else {
          log.warn("Object variable '{}' will not be imported due to unsupported serializationDataFormat: {}. " +
                     "Object variables must have serializationDataFormat application/json.",
                   pluginVariableDto.getName(), serializationDataFormat
          );
        }
      } else {
        resultList.add(pluginVariableDto);
      }
    }
    return resultList;
  }

  private void flattenJsonObjectVariable(final PluginVariableDto variable, final List<PluginVariableDto> resultList) {
    try {
      new JsonFlattener(new JacksonJsonCore(objectMapper), variable.getValue())
        .withFlattenMode(FlattenMode.KEEP_ARRAYS)
        .flattenAsMap()
        .entrySet()
        .stream()
        .map(e -> mapToFlattenedVariable(e.getKey(), e.getValue(), variable))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .forEach(resultList::add);
    } catch (Exception exception) {
      log.error(
        "Error while flattening json object variable with name '{}'.",
        variable.getName(),
        exception
      );
    }
  }

  private Optional<PluginVariableDto> mapToFlattenedVariable(final String name, final Object value,
                                                             final PluginVariableDto origin) {
    if (value == null) {
      log.info("Variable attribute '{}' of '{}' is null and won't be imported", name, origin.getName());
      return Optional.empty();
    }

    PluginVariableDto newVariable = createNewVariable(origin);

    if (JsonFlattener.ROOT.equals(name)) {
      // the name "root" is used by the flattener if the JSON is a string or array (no object)
      newVariable.setName(origin.getName());
    } else {
      newVariable.setName(String.join(".", origin.getName(), name));
    }

    if (value instanceof JsonifyArrayList) {
      newVariable.setName(String.join(".", newVariable.getName(), LIST_SIZE_VARIABLE_SUFFIX));
      newVariable.setType(VariableType.LONG.getId());
      newVariable.setValue(String.valueOf(((JsonifyArrayList<?>) value).size()));
    } else if (value instanceof String) {
      String stringValue = String.valueOf(value);
      final Optional<OffsetDateTime> optDate = parsePossibleDate(stringValue);
      if (optDate.isPresent()) {
        newVariable.setType(VariableType.DATE.getId());
        newVariable.setValue(OPTIMIZE_DATE_TIME_FORMATTER.format(optDate.get()));
      } else {
        newVariable.setType(VariableType.STRING.getId());
        newVariable.setValue(parseString(newVariable.getName(), stringValue));
      }
    } else if (value instanceof Boolean) {
      newVariable.setType(VariableType.BOOLEAN.getId());
      newVariable.setValue(String.valueOf(value));
    } else if (value instanceof BigDecimal || value instanceof Double) {
      newVariable.setType(VariableType.DOUBLE.getId());
      newVariable.setValue(value.toString());
    } else if (value instanceof Long) {
      newVariable.setType(VariableType.LONG.getId());
      newVariable.setValue(value.toString());
    } else if (value instanceof Integer) {
      newVariable.setType(VariableType.LONG.getId());
      newVariable.setValue(((Integer) value).toString());
    } else if (value instanceof Short) {
      newVariable.setType(VariableType.LONG.getId());
      newVariable.setValue(((Short) value).toString());
    } else {
      log.warn(
        "Variable attribute '{}' of '{}' with type {} and value '{}' is not supported and won't be imported.",
        name, origin.getName(), value.getClass().getSimpleName(), value
      );
      return Optional.empty();
    }

    // the ID needs to be unique for each new variable instance but consistent so that version updates get overridden
    newVariable.setId(origin.getId() + "_" + newVariable.getName());

    return Optional.of(newVariable);
  }

  private PluginVariableDto createNewVariable(final PluginVariableDto origin) {
    PluginVariableDto newVariable = new PluginVariableDto();
    newVariable.setEngineAlias(origin.getEngineAlias());
    newVariable.setProcessDefinitionId(origin.getProcessDefinitionId());
    newVariable.setProcessDefinitionKey(origin.getProcessDefinitionKey());
    newVariable.setProcessInstanceId(origin.getProcessInstanceId());
    newVariable.setVersion(origin.getVersion());
    newVariable.setTimestamp(origin.getTimestamp());
    return newVariable;
  }

  private Optional<OffsetDateTime> parsePossibleDate(final String dateAsString) {
    try {
      return Optional.of(DateParserUtils.parseOffsetDateTime(dateAsString));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  private String parseString(final String variableName, final String value) {
    if (value.length() > VALUE_MAX_LENGTH) {
      log.warn("String value of variable {} will be truncated to {} characters (original size: {}).",
               variableName, VALUE_MAX_LENGTH, value.length()
      );
      return value.substring(0, VALUE_MAX_LENGTH);
    }
    return value;
  }

}
