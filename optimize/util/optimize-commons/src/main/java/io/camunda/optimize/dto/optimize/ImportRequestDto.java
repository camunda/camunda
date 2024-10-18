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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
