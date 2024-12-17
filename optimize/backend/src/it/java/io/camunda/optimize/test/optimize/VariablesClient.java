/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.optimize;

import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.VARIABLE_TYPE_OBJECT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.optimize.rest.optimize.dto.VariableDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.text.SimpleDateFormat;
import java.util.Map;
import org.springframework.http.MediaType;

public class VariablesClient {

  private final ObjectMapper objectMapper;

  public VariablesClient() {
    objectMapper =
        new ObjectMapper()
            .setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT))
            .enable(SerializationFeature.INDENT_OUTPUT);
  }

  public VariableDto createMapJsonObjectVariableDto(final Map<String, Object> variable) {
    try {
      return createJsonObjectVariableDto(
          objectMapper.writeValueAsString(variable), "java.util.HashMap");
    } catch (final JsonProcessingException e) {
      throw new OptimizeRuntimeException(e);
    }
  }

  public VariableDto createJsonObjectVariableDto(final String value, final String objectTypeName) {
    final VariableDto objectVariableDto = new VariableDto();
    objectVariableDto.setType(VARIABLE_TYPE_OBJECT);
    objectVariableDto.setValue(value);
    final VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName(objectTypeName);
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON_VALUE);
    objectVariableDto.setValueInfo(info);
    return objectVariableDto;
  }
}
