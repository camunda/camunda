/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.plugin.importing.variable.PluginVariableDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.service.es.schema.index.ExternalProcessVariableIndex.SERIALIZATION_DATA_FORMAT;

@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExternalProcessVariableDto implements OptimizeDto {
  private String variableId;
  private String variableName;
  private String variableValue;
  private VariableType variableType;
  private Long ingestionTimestamp;
  private String processInstanceId;
  private String processDefinitionKey;
  private String serializationDataFormat; // optional, used for object variables

  public static List<PluginVariableDto> toPluginVariableDtos(final List<ExternalProcessVariableDto> variableDtos) {
    final Map<String, Object> valueInfo = new HashMap<>();
    return variableDtos.stream()
      .map(varDto -> {
        valueInfo.put(SERIALIZATION_DATA_FORMAT, varDto.getSerializationDataFormat());
        return new PluginVariableDto()
          .setTimestamp(OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(varDto.getIngestionTimestamp()),
            ZoneId.systemDefault()
          ))
          .setId(varDto.getVariableId())
          .setName(varDto.getVariableName())
          .setValue(varDto.getVariableValue())
          .setType(varDto.getVariableType().getId())
          .setValueInfo(valueInfo)
          .setProcessInstanceId(varDto.getProcessInstanceId())
          .setProcessDefinitionKey(varDto.getProcessDefinitionKey());
      })
      .collect(toList());
  }
}
