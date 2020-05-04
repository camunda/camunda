/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.ScoreDoc;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataDto;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdentitySearchResultDto {
  private long total;
  private List<IdentityWithMetadataDto> result = new ArrayList<>();

  /**
   * ScoreDoc holds a reference to the ScoreDoc of the last result the result list.
   * Used to paginate through the searchableIdentityCache.
   */
  @JsonIgnore
  private ScoreDoc scoreDoc;

  public IdentitySearchResultDto(final long total, final List<IdentityWithMetadataDto> result) {
    this.total = total;
    this.result = result;
  }
}
