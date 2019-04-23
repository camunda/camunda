/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable.value;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;

@Data
public abstract class VariableInstanceDto<T> implements OptimizeDto {

  private String id;
  private String name;
  private String type;
  private Long version;

  public abstract T getValue();
}
