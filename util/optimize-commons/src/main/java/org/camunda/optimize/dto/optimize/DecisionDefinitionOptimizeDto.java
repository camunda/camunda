/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class DecisionDefinitionOptimizeDto extends DefinitionOptimizeResponseDto {
  private String dmn10Xml;
  private List<DecisionVariableNameResponseDto> inputVariableNames = new ArrayList<>();
  private List<DecisionVariableNameResponseDto> outputVariableNames = new ArrayList<>();

  public DecisionDefinitionOptimizeDto() {
    setType(DefinitionType.DECISION);
  }

  public DecisionDefinitionOptimizeDto(final String id,
                                       final EngineDataSourceDto dataSource,
                                       final String dmn10Xml,
                                       final List<DecisionVariableNameResponseDto> inputVariableNames,
                                       final List<DecisionVariableNameResponseDto> outputVariableNames) {
    super(id, dataSource);
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }

  @Builder
  public DecisionDefinitionOptimizeDto(final String id,
                                       final String key,
                                       final String version,
                                       final String versionTag,
                                       final String name,
                                       final DataSourceDto dataSource,
                                       final String tenantId,
                                       final String dmn10Xml,
                                       final boolean deleted,
                                       final List<DecisionVariableNameResponseDto> inputVariableNames,
                                       final List<DecisionVariableNameResponseDto> outputVariableNames) {
    super(id, key, version, versionTag, name, dataSource, tenantId, deleted, DefinitionType.DECISION);
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }
}
