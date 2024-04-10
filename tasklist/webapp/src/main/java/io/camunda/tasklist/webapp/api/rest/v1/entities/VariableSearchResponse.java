/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import io.camunda.tasklist.entities.DraftTaskVariableEntity;
import io.camunda.tasklist.entities.TaskVariableEntity;
import io.camunda.tasklist.entities.VariableEntity;
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
        .setValue(variableEntity.getFullValue())
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

  public static VariableSearchResponse createFrom(TaskVariableEntity variableEntity) {
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
