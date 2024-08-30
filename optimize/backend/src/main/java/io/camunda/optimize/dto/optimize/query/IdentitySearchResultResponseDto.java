/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.ScoreDoc;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdentitySearchResultResponseDto {
  private List<IdentityWithMetadataResponseDto> result = new ArrayList<>();

  /**
   * ScoreDoc holds a reference to the ScoreDoc of the last result the result list. Used to paginate
   * through the searchableIdentityCache.
   */
  @JsonIgnore private ScoreDoc scoreDoc;

  public IdentitySearchResultResponseDto(final List<IdentityWithMetadataResponseDto> result) {
    this.result = result;
  }
}
