/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(asEnum = true)
public class DecisionDefinitionOptimizeDto extends DefinitionOptimizeDto {
  private String dmn10Xml;
  private List<DecisionVariableNameDto> inputVariableNames = new ArrayList<>();
  private List<DecisionVariableNameDto> outputVariableNames = new ArrayList<>();

  public DecisionDefinitionOptimizeDto(final String id,
                                       final String key,
                                       final String version,
                                       final String versionTag,
                                       final String name,
                                       final String engine,
                                       final String tenantId) {
    super(id, key, version, versionTag, name, engine, tenantId);
  }

  public DecisionDefinitionOptimizeDto(final String id,
                                       final String engine,
                                       final String dmn10Xml,
                                       final List<DecisionVariableNameDto> inputVariableNames,
                                       final List<DecisionVariableNameDto> outputVariableNames) {
    super(id, engine);
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
                                       final String engine,
                                       final String tenantId,
                                       final String dmn10Xml,
                                       final List<DecisionVariableNameDto> inputVariableNames,
                                       final List<DecisionVariableNameDto> outputVariableNames) {
    super(id, key, version, versionTag, name, engine, tenantId);
    this.dmn10Xml = dmn10Xml;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }
}
