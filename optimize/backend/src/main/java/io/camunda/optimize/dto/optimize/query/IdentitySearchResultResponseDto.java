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
    final int PRIME = 59;
    int result = 1;
    final Object $result = getResult();
    result = result * PRIME + ($result == null ? 43 : $result.hashCode());
    final Object $scoreDoc = getScoreDoc();
    result = result * PRIME + ($scoreDoc == null ? 43 : $scoreDoc.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof IdentitySearchResultResponseDto)) {
      return false;
    }
    final IdentitySearchResultResponseDto other = (IdentitySearchResultResponseDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$result = getResult();
    final Object other$result = other.getResult();
    if (this$result == null ? other$result != null : !this$result.equals(other$result)) {
      return false;
    }
    final Object this$scoreDoc = getScoreDoc();
    final Object other$scoreDoc = other.getScoreDoc();
    if (this$scoreDoc == null ? other$scoreDoc != null : !this$scoreDoc.equals(other$scoreDoc)) {
      return false;
    }
    return true;
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
