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
import java.util.Objects;
import org.apache.lucene.search.ScoreDoc;

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

  public IdentitySearchResultResponseDto(
      final List<IdentityWithMetadataResponseDto> result, final ScoreDoc scoreDoc) {
    this.result = result;
    this.scoreDoc = scoreDoc;
  }

  public IdentitySearchResultResponseDto() {}

  public List<IdentityWithMetadataResponseDto> getResult() {
    return result;
  }

  public void setResult(final List<IdentityWithMetadataResponseDto> result) {
    this.result = result;
  }

  public ScoreDoc getScoreDoc() {
    return scoreDoc;
  }

  @JsonIgnore
  public void setScoreDoc(final ScoreDoc scoreDoc) {
    this.scoreDoc = scoreDoc;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof IdentitySearchResultResponseDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, scoreDoc);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final IdentitySearchResultResponseDto that = (IdentitySearchResultResponseDto) o;
    return Objects.equals(result, that.result) && Objects.equals(scoreDoc, that.scoreDoc);
  }

  @Override
  public String toString() {
    return "IdentitySearchResultResponseDto(result="
        + getResult()
        + ", scoreDoc="
        + getScoreDoc()
        + ")";
  }
}
