/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.event.process.source;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@SuperBuilder
@Getter
@Setter
@FieldNameConstants
@EqualsAndHashCode(callSuper = true)
public class CamundaEventSourceConfigDto extends EventSourceConfigDto {

  private String processDefinitionKey;
  private String processDefinitionName;
  @Builder.Default
  private List<String> versions = new ArrayList<>();
  @Builder.Default
  private List<String> tenants = new ArrayList<>();
  private boolean tracedByBusinessKey;
  private String traceVariable;

}

