/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import java.io.Serializable;
import java.util.Objects;

public class ImportIndexMismatchDto implements Serializable {

  private String indexName;
  private int sourceIndexVersion;
  private int targetIndexVersion;

  public ImportIndexMismatchDto(
      final String indexName, final int sourceIndexVersion, final int targetIndexVersion) {
    this.indexName = indexName;
    this.sourceIndexVersion = sourceIndexVersion;
    this.targetIndexVersion = targetIndexVersion;
  }

  public ImportIndexMismatchDto() {}

  public String getIndexName() {
    return indexName;
  }

  public int getSourceIndexVersion() {
    return sourceIndexVersion;
  }

  public int getTargetIndexVersion() {
    return targetIndexVersion;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ImportIndexMismatchDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, sourceIndexVersion, targetIndexVersion);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ImportIndexMismatchDto that = (ImportIndexMismatchDto) o;
    return sourceIndexVersion == that.sourceIndexVersion
        && targetIndexVersion == that.targetIndexVersion
        && Objects.equals(indexName, that.indexName);
  }

  public static ImportIndexMismatchDtoBuilder builder() {
    return new ImportIndexMismatchDtoBuilder();
  }

  public static class ImportIndexMismatchDtoBuilder {

    private String indexName;
    private int sourceIndexVersion;
    private int targetIndexVersion;

    ImportIndexMismatchDtoBuilder() {}

    public ImportIndexMismatchDtoBuilder indexName(final String indexName) {
      this.indexName = indexName;
      return this;
    }

    public ImportIndexMismatchDtoBuilder sourceIndexVersion(final int sourceIndexVersion) {
      this.sourceIndexVersion = sourceIndexVersion;
      return this;
    }

    public ImportIndexMismatchDtoBuilder targetIndexVersion(final int targetIndexVersion) {
      this.targetIndexVersion = targetIndexVersion;
      return this;
    }

    public ImportIndexMismatchDto build() {
      return new ImportIndexMismatchDto(indexName, sourceIndexVersion, targetIndexVersion);
    }

    @Override
    public String toString() {
      return "ImportIndexMismatchDto.ImportIndexMismatchDtoBuilder(indexName="
          + indexName
          + ", sourceIndexVersion="
          + sourceIndexVersion
          + ", targetIndexVersion="
          + targetIndexVersion
          + ")";
    }
  }
}
