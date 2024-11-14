/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.v86.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.v86.entities.TaskVariableEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
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

  public VariableSearchResponse setId(final String id) {
    this.id = id;
    return this;
  }

  public String getName() {
    return name;
  }

  public VariableSearchResponse setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableSearchResponse setValue(final String value) {
    this.value = value;
    return this;
  }

  public VariableSearchResponse resetValue() {
    value = null;
    return this;
  }

  public boolean getIsValueTruncated() {
    return isValueTruncated;
  }

  public VariableSearchResponse setIsValueTruncated(final boolean valueTruncated) {
    isValueTruncated = valueTruncated;
    return this;
  }

  public String getPreviewValue() {
    return previewValue;
  }

  public VariableSearchResponse setPreviewValue(final String previewValue) {
    this.previewValue = previewValue;
    return this;
  }

  public DraftSearchVariableValue getDraft() {
    return draft;
  }

  public VariableSearchResponse setDraft(final DraftSearchVariableValue draft) {
    this.draft = draft;
    return this;
  }

  public VariableSearchResponse addDraft(final DraftTaskVariableEntity draftTaskVariable) {
    draft =
        new DraftSearchVariableValue()
            .setValue(draftTaskVariable.getFullValue())
            .setIsValueTruncated(draftTaskVariable.getIsPreview())
            .setPreviewValue(draftTaskVariable.getValue());
    return this;
  }

  public static VariableSearchResponse createFrom(final VariableEntity variableEntity) {
    return new VariableSearchResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setValue(variableEntity.getFullValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setPreviewValue(variableEntity.getValue());
  }

  public static VariableSearchResponse createFrom(
      final VariableEntity variableEntity, final DraftTaskVariableEntity draftTaskVariable) {
    return createFrom(variableEntity)
        .setDraft(
            new DraftSearchVariableValue()
                .setValue(draftTaskVariable.getFullValue())
                .setIsValueTruncated(draftTaskVariable.getIsPreview())
                .setPreviewValue(draftTaskVariable.getValue()));
  }

  public static VariableSearchResponse createFrom(final DraftTaskVariableEntity draftTaskVariable) {
    return new VariableSearchResponse()
        .setId(draftTaskVariable.getId())
        .setName(draftTaskVariable.getName())
        .setDraft(
            new DraftSearchVariableValue()
                .setValue(draftTaskVariable.getFullValue())
                .setIsValueTruncated(draftTaskVariable.getIsPreview())
                .setPreviewValue(draftTaskVariable.getValue()));
  }

  public static VariableSearchResponse createFrom(final TaskVariableEntity variableEntity) {
    return new VariableSearchResponse()
        .setId(variableEntity.getId())
        .setName(variableEntity.getName())
        .setPreviewValue(variableEntity.getValue())
        .setIsValueTruncated(variableEntity.getIsPreview())
        .setValue(variableEntity.getFullValue());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, value, isValueTruncated, previewValue, draft);
  }

  @Override
  public boolean equals(final Object o) {
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

    public DraftSearchVariableValue setValue(final String value) {
      this.value = value;
      return this;
    }

    public DraftSearchVariableValue resetValue() {
      value = null;
      return this;
    }

    public boolean getIsValueTruncated() {
      return isValueTruncated;
    }

    public DraftSearchVariableValue setIsValueTruncated(final boolean valueTruncated) {
      isValueTruncated = valueTruncated;
      return this;
    }

    public String getPreviewValue() {
      return previewValue;
    }

    public DraftSearchVariableValue setPreviewValue(final String previewValue) {
      this.previewValue = previewValue;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(value, isValueTruncated, previewValue);
    }

    @Override
    public boolean equals(final Object o) {
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
    public String toString() {
      return new StringJoiner(", ", DraftSearchVariableValue.class.getSimpleName() + "[", "]")
          .add("value='" + value + "'")
          .add("isValueTruncated=" + isValueTruncated)
          .add("previewValue='" + previewValue + "'")
          .toString();
    }
  }
}
