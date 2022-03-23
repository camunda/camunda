/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AssigneeCandidateGroupReportSearchRequestDto {
  private String terms;
  @Builder.Default
  private int limit = 25;
  @NotNull
  @NotEmpty
  private List<String> reportIds;

  public Optional<String> getTerms() {
    return Optional.ofNullable(terms);
  }
}
