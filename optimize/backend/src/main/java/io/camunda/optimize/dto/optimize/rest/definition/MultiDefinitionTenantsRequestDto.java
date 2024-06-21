/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.definition;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class MultiDefinitionTenantsRequestDto {

  private List<DefinitionDto> definitions = new ArrayList<>();
  private String filterByCollectionScope;

  public MultiDefinitionTenantsRequestDto(final List<DefinitionDto> definitionDtos) {
    definitions = definitionDtos;
  }

  @AllArgsConstructor
  @Data
  @NoArgsConstructor
  public static class DefinitionDto {

    @NotNull
    private String key;
    @NotNull
    private List<String> versions = new ArrayList<>();
  }
}
