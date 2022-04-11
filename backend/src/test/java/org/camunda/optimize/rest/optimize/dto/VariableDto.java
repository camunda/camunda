/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.optimize.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;

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
