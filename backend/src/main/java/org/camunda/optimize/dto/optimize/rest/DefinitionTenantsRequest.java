/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefinitionTenantsRequest {
  @NotNull
  @Builder.Default
  private List<String> versions = new ArrayList<>();

  private String filterByCollectionScope;

  public Optional<String> getFilterByCollectionScope() {
    return Optional.ofNullable(filterByCollectionScope);
  }
}
