/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.lucene.search.ScoreDoc;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IdentitySearchResultResponseDto {
  private long total;
  private List<IdentityWithMetadataResponseDto> result = new ArrayList<>();

  /**
   * ScoreDoc holds a reference to the ScoreDoc of the last result the result list.
   * Used to paginate through the searchableIdentityCache.
   */
  @JsonIgnore
  private ScoreDoc scoreDoc;

  public IdentitySearchResultResponseDto(final long total, final List<IdentityWithMetadataResponseDto> result) {
    this.total = total;
    this.result = result;
  }
}
