/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SimpleDefinitionDto {

  @EqualsAndHashCode.Include @NonNull private String key;
  private String name;
  @EqualsAndHashCode.Include @NonNull private DefinitionType type;
  @NonNull private Set<String> engines = new HashSet<>();

  public SimpleDefinitionDto(
      @NonNull final String key,
      final String name,
      @NonNull final DefinitionType type,
      @NonNull final String engine) {
    this.key = key;
    this.name = name;
    this.type = type;
    engines = Collections.singleton(engine);
  }

  public SimpleDefinitionDto(
      @NonNull String key,
      String name,
      @NonNull DefinitionType type,
      @NonNull Set<String> engines) {
    this.key = key;
    this.name = name;
    this.type = type;
    this.engines = engines;
  }

  protected SimpleDefinitionDto() {}
}
