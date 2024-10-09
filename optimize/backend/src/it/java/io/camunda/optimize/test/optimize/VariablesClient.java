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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.camunda.optimize.rest.optimize.dto.VariableDto;
import jakarta.ws.rs.core.MediaType;
import java.text.SimpleDateFormat;
import java.util.Map;
import lombok.SneakyThrows;

public class VariablesClient {
  private final ObjectMapper objectMapper;

  public VariablesClient() {
    objectMapper =
        new ObjectMapper()
            .setDateFormat(new SimpleDateFormat(OPTIMIZE_DATE_FORMAT))
            .enable(SerializationFeature.INDENT_OUTPUT);
  }

  @SneakyThrows
  public VariableDto createMapJsonObjectVariableDto(final Map<String, Object> variable) {
    return createJsonObjectVariableDto(
        objectMapper.writeValueAsString(variable), "java.util.HashMap");
  }

  @SneakyThrows
  public VariableDto createJsonObjectVariableDto(final String value, final String objectTypeName) {
    VariableDto objectVariableDto = new VariableDto();
    objectVariableDto.setType(VARIABLE_TYPE_OBJECT);
    objectVariableDto.setValue(value);
    VariableDto.ValueInfo info = new VariableDto.ValueInfo();
    info.setObjectTypeName(objectTypeName);
    info.setSerializationDataFormat(MediaType.APPLICATION_JSON);
    objectVariableDto.setValueInfo(info);
    return objectVariableDto;
  }
}
