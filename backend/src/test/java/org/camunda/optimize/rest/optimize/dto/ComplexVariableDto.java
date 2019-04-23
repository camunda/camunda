/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.optimize.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

public class ComplexVariableDto implements Serializable {

  @Getter @Setter private String type;
  @Getter @Setter private Object value;

  @Getter @Setter private ValueInfo valueInfo;

  public static class ValueInfo {
    @Getter @Setter private String objectTypeName;
    @Getter @Setter private String serializationDataFormat;
  }

}
