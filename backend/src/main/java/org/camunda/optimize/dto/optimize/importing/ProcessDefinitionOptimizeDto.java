/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.query.definition.KeyDefinitionOptimizeDto;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@Data
public class ProcessDefinitionOptimizeDto
    extends KeyDefinitionOptimizeDto
    implements Serializable, OptimizeDto {

  protected String id;
  protected String name;
  protected String version;
  protected String engine;
  protected String bpmn20Xml;
  protected Map<String, String> flowNodeNames = new HashMap<>();
}
