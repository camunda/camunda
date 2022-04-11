/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldNameConstants
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SimpleProcessVariableDto {
  @EqualsAndHashCode.Include
  private String id;
  private String name;
  private String type;
  private List<String> value;
  @EqualsAndHashCode.Include
  private long version;
}
