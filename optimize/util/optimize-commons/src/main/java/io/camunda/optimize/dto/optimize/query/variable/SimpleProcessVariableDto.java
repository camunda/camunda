/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SimpleProcessVariableDto {

  @EqualsAndHashCode.Include private String id;
  private String name;
  private String type;
  private List<String> value;
  @EqualsAndHashCode.Include private long version;

  public static final class Fields {

    public static final String id = "id";
    public static final String name = "name";
    public static final String type = "type";
    public static final String value = "value";
    public static final String version = "version";
  }
}
