/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.definition;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MultiDefinitionTenantsRequestDto {
  @NotNull
  @Singular
  private List<DefinitionDto> definitions = new ArrayList<>();

  private String filterByCollectionScope;

  @AllArgsConstructor
  @Builder
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class DefinitionDto {
    @NotNull
    private String key;
    @NotNull
    @Singular
    private List<String> versions = new ArrayList<>();
  }
}
