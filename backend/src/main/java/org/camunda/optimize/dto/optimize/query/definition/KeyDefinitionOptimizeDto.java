/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

@Data
public class KeyDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected String key;
}
