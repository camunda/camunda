/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.optimize.dto;

import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class VariableDto implements Serializable {

  private String type;
  private Object value;

  private ValueInfo valueInfo = new ValueInfo();

  @Getter
  @Setter
  public static class ValueInfo {
    private String objectTypeName;
    private String serializationDataFormat;
  }
}
