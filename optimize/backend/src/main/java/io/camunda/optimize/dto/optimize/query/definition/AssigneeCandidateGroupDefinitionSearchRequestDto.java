/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.definition;

import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
public class AssigneeCandidateGroupDefinitionSearchRequestDto {

  private String terms;
  @Builder.Default private int limit = 25;
  @NotNull private String processDefinitionKey;

  @Builder.Default
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));

  public AssigneeCandidateGroupDefinitionSearchRequestDto(
      String terms, int limit, @NotNull String processDefinitionKey, List<String> tenantIds) {
    this.terms = terms;
    this.limit = limit;
    this.processDefinitionKey = processDefinitionKey;
    this.tenantIds = tenantIds;
  }

  public Optional<String> getTerms() {
    return Optional.ofNullable(terms);
  }
}
