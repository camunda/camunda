/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import io.camunda.optimize.service.db.schema.ScriptData;

public class ImportRequestDto {

  private String importName;
  private String indexName;
  private ScriptData scriptData;
  private String id;
  private Object source;
  private RequestType type;
  private int retryNumberOnConflict;

  ImportRequestDto(
      final String importName,
      final String indexName,
      final ScriptData scriptData,
      final String id,
      final Object source,
      final RequestType type,
      final int retryNumberOnConflict) {
    this.importName = importName;
    this.indexName = indexName;
    this.scriptData = scriptData;
    this.id = id;
    this.source = source;
    this.type = type;
    this.retryNumberOnConflict = retryNumberOnConflict;
  }

  public String getImportName() {
    return importName;
  }

  public void setImportName(final String importName) {
    this.importName = importName;
  }

  public String getIndexName() {
    return indexName;
  }

  public void setIndexName(final String indexName) {
    this.indexName = indexName;
  }

  public ScriptData getScriptData() {
    return scriptData;
  }

  public void setScriptData(final ScriptData scriptData) {
    this.scriptData = scriptData;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Object getSource() {
    return source;
  }

  public void setSource(final Object source) {
    this.source = source;
  }

  public RequestType getType() {
    return type;
  }

  public void setType(final RequestType type) {
    this.type = type;
  }

  public int getRetryNumberOnConflict() {
    return retryNumberOnConflict;
  }

  public void setRetryNumberOnConflict(final int retryNumberOnConflict) {
    this.retryNumberOnConflict = retryNumberOnConflict;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ImportRequestDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $importName = getImportName();
    result = result * PRIME + ($importName == null ? 43 : $importName.hashCode());
    final Object $indexName = getIndexName();
    result = result * PRIME + ($indexName == null ? 43 : $indexName.hashCode());
    final Object $scriptData = getScriptData();
    result = result * PRIME + ($scriptData == null ? 43 : $scriptData.hashCode());
    final Object $id = getId();
    result = result * PRIME + ($id == null ? 43 : $id.hashCode());
    final Object $source = getSource();
    result = result * PRIME + ($source == null ? 43 : $source.hashCode());
    final Object $type = getType();
    result = result * PRIME + ($type == null ? 43 : $type.hashCode());
    result = result * PRIME + getRetryNumberOnConflict();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ImportRequestDto)) {
      return false;
    }
    final ImportRequestDto other = (ImportRequestDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$importName = getImportName();
    final Object other$importName = other.getImportName();
    if (this$importName == null
        ? other$importName != null
        : !this$importName.equals(other$importName)) {
      return false;
    }
    final Object this$indexName = getIndexName();
    final Object other$indexName = other.getIndexName();
    if (this$indexName == null
        ? other$indexName != null
        : !this$indexName.equals(other$indexName)) {
      return false;
    }
    final Object this$scriptData = getScriptData();
    final Object other$scriptData = other.getScriptData();
    if (this$scriptData == null
        ? other$scriptData != null
        : !this$scriptData.equals(other$scriptData)) {
      return false;
    }
    final Object this$id = getId();
    final Object other$id = other.getId();
    if (this$id == null ? other$id != null : !this$id.equals(other$id)) {
      return false;
    }
    final Object this$source = getSource();
    final Object other$source = other.getSource();
    if (this$source == null ? other$source != null : !this$source.equals(other$source)) {
      return false;
    }
    final Object this$type = getType();
    final Object other$type = other.getType();
    if (this$type == null ? other$type != null : !this$type.equals(other$type)) {
      return false;
    }
    if (getRetryNumberOnConflict() != other.getRetryNumberOnConflict()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ImportRequestDto(importName="
        + getImportName()
        + ", indexName="
        + getIndexName()
        + ", scriptData="
        + getScriptData()
        + ", id="
        + getId()
        + ", source="
        + getSource()
        + ", type="
        + getType()
        + ", retryNumberOnConflict="
        + getRetryNumberOnConflict()
        + ")";
  }

  public static ImportRequestDtoBuilder builder() {
    return new ImportRequestDtoBuilder();
  }

  public static class ImportRequestDtoBuilder {

    private String importName;
    private String indexName;
    private ScriptData scriptData;
    private String id;
    private Object source;
    private RequestType type;
    private int retryNumberOnConflict;

    ImportRequestDtoBuilder() {}

    public ImportRequestDtoBuilder importName(final String importName) {
      this.importName = importName;
      return this;
    }

    public ImportRequestDtoBuilder indexName(final String indexName) {
      this.indexName = indexName;
      return this;
    }

    public ImportRequestDtoBuilder scriptData(final ScriptData scriptData) {
      this.scriptData = scriptData;
      return this;
    }

    public ImportRequestDtoBuilder id(final String id) {
      this.id = id;
      return this;
    }

    public ImportRequestDtoBuilder source(final Object source) {
      this.source = source;
      return this;
    }

    public ImportRequestDtoBuilder type(final RequestType type) {
      this.type = type;
      return this;
    }

    public ImportRequestDtoBuilder retryNumberOnConflict(final int retryNumberOnConflict) {
      this.retryNumberOnConflict = retryNumberOnConflict;
      return this;
    }

    public ImportRequestDto build() {
      return new ImportRequestDto(
          importName, indexName, scriptData, id, source, type, retryNumberOnConflict);
    }

    @Override
    public String toString() {
      return "ImportRequestDto.ImportRequestDtoBuilder(importName="
          + importName
          + ", indexName="
          + indexName
          + ", scriptData="
          + scriptData
          + ", id="
          + id
          + ", source="
          + source
          + ", type="
          + type
          + ", retryNumberOnConflict="
          + retryNumberOnConflict
          + ")";
    }
  }

  public enum Fields {
    importName,
    indexName,
    scriptData,
    id,
    source,
    type,
    retryNumberOnConflict
  }
}
