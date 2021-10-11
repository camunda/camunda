/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.zeebe.variable;

import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class ZeebeVariableDataDto implements VariableRecordValue {

  private String name;
  private String value;
  private long scopeKey;
  private long processInstanceKey;
  private long processDefinitionKey;

  @Override
  public String toJson() {
    throw new UnsupportedOperationException("Operation not supported");
  }
}
