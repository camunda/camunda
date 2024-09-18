/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefinitionTenantsRequestDto {

  @Builder.Default private List<String> versions = new ArrayList<>();

  private String filterByCollectionScope;

  public DefinitionTenantsRequestDto(List<String> versions, String filterByCollectionScope) {
    this.versions = versions;
    this.filterByCollectionScope = filterByCollectionScope;
  }
}
