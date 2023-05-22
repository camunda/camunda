/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.definition;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Data
@NoArgsConstructor
public class MultiDefinitionTenantsRequestDto {

  public MultiDefinitionTenantsRequestDto(final List<DefinitionDto> definitionDtos) {
    this.definitions = definitionDtos;
  }

  private List<DefinitionDto> definitions = new ArrayList<>();
  private String filterByCollectionScope;

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
