/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.webapps.schema.entities.VariableEntity;
import io.camunda.webapps.schema.entities.tasklist.DraftTaskVariableEntity;
import io.camunda.webapps.schema.entities.tasklist.SnapshotTaskVariableEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;
import java.util.StringJoiner;

public class VariableSearchResponse {
  @Schema(description = "The unique identifier of the variable.")
  private String id;

  @Schema(description = "The name of the variable.")
  private String name;

  @Schema(description = "The value of the variable.")
  private String value;

  @Schema(description = "Does the `previewValue` contain the truncated value or full value?")
  private boolean isValueTruncated;

  @Schema(description = "A preview of the variable's value. Limited in size.")
  private String previewValue;

  @Schema(description = "The draft value of the variable.")
  private DraftSearchVariableValue draft;

  public String getId() {
    return id;
  }

  public VariableSearchResponse setId(String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableSearchResponse setName(String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableSearchResponse setValue(String value) {
    this.value = value;
    return this;
  }

  public VariableSearchResponse resetValue() {
    this.value = null;
    return this;
  }

  public boolean getIsValueTruncated() {
    return isValueTruncated;
  }

  public VariableSearchResponse setIsValueTruncated(boolean valueTruncated) {
    isValueTruncated = valueTruncated;
    return this;
  }

  public String getPreviewValue() {
    return previewValue;
  }

  public VariableSearchResponse setPreviewValue(String previewValue) {
    this.previewValue = previewValue;
    return this;
  }

  public DraftSearchVariableValue getDraft() {
    return draft;
  }

  public VariableSearchResponse setDraft(DraftSearchVariableValue draft) {
    this.draft = draft;
    return this;
  }

  public VariableSearchResponse addDraft(DraftTaskVariableEntity draftTaskVariable) {
    this.draft =
        new DraftSearchVariableValue()
            .setValue(draftTaskVariable.getFullValue())
            .setIsValueTruncated(draftTaskVariable.getIsPreview())
            .setPreviewValue(draftTaskVariable.getValue());
    return this;
  }

  public static VariableSearchResponse createFrom(VariableEntity variableEntity) {
    return new VariableSearchResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(
            variableEntity.getIsPreview()
                ? variableEntity.getFullValue()
                : variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setPreviewValue(variableEntity.getValue());
  }

  public static VariableSearchResponse createFrom(
      VariableEntity variableEntity, DraftTaskVariableEntity draftTaskVariable) {
    return createFrom(variableEntity)
        .setDraft(
            new DraftSearchVariableValue()
                .setValue(draftTaskVariable.getFullValue())
                .setIsValueTruncated(draftTaskVariable.getIsPreview())
                .setPreviewValue(draftTaskVariable.getValue()));
  }

  public static VariableSearchResponse createFrom(DraftTaskVariableEntity draftTaskVariable) {
    return new VariableSearchResponse()
        .setId(draftTaskVariable.getId())
        .setName(draftTaskVariable.getName())
        .setDraft(
            new DraftSearchVariableValue()
                .setValue(draftTaskVariable.getFullValue())
                .setIsValueTruncated(draftTaskVariable.getIsPreview())
                .setPreviewValue(draftTaskVariable.getValue()));
  }

  public static VariableSearchResponse createFrom(SnapshotTaskVariableEntity variableEntity) {
    return new VariableSearchResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(variableEntity.getFullValue());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableSearchResponse that = (VariableSearchResponse) o;
    return isValueTruncated == that.isValueTruncated
        && Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Objects.equals(previewValue, that.previewValue)
        && Objects.equals(draft, that.draft);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, isValueTruncated, previewValue, draft);
  }

  @Override
  public String toString() {
    return new StringJoiner(", ", VariableSearchResponse.class.getSimpleName() + "[", "]")
        .add("id='" + id + "'")
        .add("name='" + name + "'")
        .add("value='" + value + "'")
        .add("isValueTruncated=" + isValueTruncated)
        .add("previewValue='" + previewValue + "'")
        .add("draft=" + draft)
        .toString();
  }

  public static class DraftSearchVariableValue {
    @Schema(description = "The value of the variable.")
    private String value;

    @Schema(description = "Does the `previewValue` contain the truncated value or full value?")
    private boolean isValueTruncated;

    @Schema(description = "A preview of the variable's value. Limited in size.")
    private String previewValue;

    public String getValue() {
      return value;
    }

    public DraftSearchVariableValue setValue(String value) {
      this.value = value;
      return this;
    }

    public DraftSearchVariableValue resetValue() {
      this.value = null;
      return this;
    }

    public boolean getIsValueTruncated() {
      return isValueTruncated;
    }

    public DraftSearchVariableValue setIsValueTruncated(boolean valueTruncated) {
      isValueTruncated = valueTruncated;
      return this;
    }

    public String getPreviewValue() {
      return previewValue;
    }

    public DraftSearchVariableValue setPreviewValue(String previewValue) {
      this.previewValue = previewValue;
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final DraftSearchVariableValue that = (DraftSearchVariableValue) o;
      return isValueTruncated == that.isValueTruncated
          && Objects.equals(value, that.value)
          && Objects.equals(previewValue, that.previewValue);
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, isValueTruncated, previewValue);
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", DraftSearchVariableValue.class.getSimpleName() + "[", "]")
          .add("value='" + value + "'")
          .add("isValueTruncated=" + isValueTruncated)
          .add("previewValue='" + previewValue + "'")
          .toString();
    }
  }
}
