/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.camunda.optimize.dto.optimize.query.definition.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;

import java.util.ArrayList;
import java.util.List;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
public class DecisionDefinitionOptimizeDto implements DefinitionOptimizeDto {
  private String id;
  private String key;
  private String version;
  private String versionTag;
  private String name;
  private String dmn10Xml;
  private String engine;
  private String tenantId;
  private List<DecisionVariableNameDto> inputVariableNames = new ArrayList<>();
  private List<DecisionVariableNameDto> outputVariableNames = new ArrayList<>();

  public DecisionDefinitionOptimizeDto(final String id, final String key, final String version, final String versionTag,
                                       final String name, final String engine, final String tenantId) {
    this.id = id;
    this.key = key;
    this.version = version;
    this.versionTag = versionTag;
    this.name = name;
    this.engine = engine;
    this.tenantId = tenantId;
  }

  public DecisionDefinitionOptimizeDto(final String id,
                                       final String dmn10Xml,
                                       final String engine,
                                       final List<DecisionVariableNameDto> inputVariableNames,
                                       final List<DecisionVariableNameDto> outputVariableNames) {
    this.id = id;
    this.dmn10Xml = dmn10Xml;
    this.engine = engine;
    this.inputVariableNames = inputVariableNames;
    this.outputVariableNames = outputVariableNames;
  }
}
