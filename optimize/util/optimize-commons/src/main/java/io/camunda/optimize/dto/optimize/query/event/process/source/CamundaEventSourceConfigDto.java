/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.event.process.source;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class CamundaEventSourceConfigDto extends EventSourceConfigDto {

  private String processDefinitionKey;
  private String processDefinitionName;
  @Builder.Default private List<String> versions = new ArrayList<>();
  @Builder.Default private List<String> tenants = new ArrayList<>();
  private boolean tracedByBusinessKey;
  private String traceVariable;

  public static final class Fields {

    public static final String processDefinitionKey = "processDefinitionKey";
    public static final String processDefinitionName = "processDefinitionName";
    public static final String versions = "versions";
    public static final String tenants = "tenants";
    public static final String tracedByBusinessKey = "tracedByBusinessKey";
    public static final String traceVariable = "traceVariable";
  }
}
