/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class VariableUpdateInstanceDto implements OptimizeDto {

  private String instanceId;
  private String name;
  private String type;
  private List<String> value;
  private String processInstanceId;
  private String tenantId;
  private OffsetDateTime timestamp;

  public static final class Fields {

    public static final String instanceId = "instanceId";
    public static final String name = "name";
    public static final String type = "type";
    public static final String value = "value";
    public static final String processInstanceId = "processInstanceId";
    public static final String tenantId = "tenantId";
    public static final String timestamp = "timestamp";
  }
}
