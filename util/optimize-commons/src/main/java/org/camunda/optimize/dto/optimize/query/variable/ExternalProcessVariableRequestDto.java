/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

import static java.util.stream.Collectors.toList;

@NoArgsConstructor
@Data
@Accessors(chain = true)
@FieldNameConstants
public class ExternalProcessVariableRequestDto implements OptimizeDto {
  @NotBlank
  private String id;
  @NotBlank
  private String name;
  private String value;
  @NotNull
  private VariableType type;
  @NotBlank
  private String processInstanceId;
  @NotBlank
  private String processDefinitionKey;
  private String serializationDataFormat; // optional, used for object variables

  public static List<ExternalProcessVariableDto> toExternalProcessVariableDtos(final Long ingestionTimestamp,
                                                                               final List<ExternalProcessVariableRequestDto> variableDtos) {
    return variableDtos.stream()
      .map(varDto -> new ExternalProcessVariableDto()
        .setIngestionTimestamp(ingestionTimestamp)
        .setVariableId(varDto.getId())
        .setVariableName(varDto.getName())
        .setVariableValue(varDto.getValue())
        .setVariableType(varDto.getType())
        .setProcessInstanceId(varDto.getProcessInstanceId())
        .setProcessDefinitionKey(varDto.getProcessDefinitionKey())
        .setSerializationDataFormat(varDto.getSerializationDataFormat()))
      .collect(toList());
  }
}
