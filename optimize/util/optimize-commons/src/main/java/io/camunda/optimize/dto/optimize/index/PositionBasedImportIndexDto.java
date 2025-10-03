/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import java.util.Objects;

public class PositionBasedImportIndexDto extends ImportIndexDto<ZeebeDataSourceDto> {

  protected long positionOfLastEntity = 0;
  protected long sequenceOfLastEntity = 0;
  // flag to indicate whether at least one record with a sequence field has been imported
  protected boolean hasSeenSequenceField = false;

  public PositionBasedImportIndexDto() {}

  public long getPositionOfLastEntity() {
    return positionOfLastEntity;
  }

  public void setPositionOfLastEntity(final long positionOfLastEntity) {
    this.positionOfLastEntity = positionOfLastEntity;
  }

  public long getSequenceOfLastEntity() {
    return sequenceOfLastEntity;
  }

  public void setSequenceOfLastEntity(final long sequenceOfLastEntity) {
    this.sequenceOfLastEntity = sequenceOfLastEntity;
  }

  public boolean isHasSeenSequenceField() {
    return hasSeenSequenceField;
  }

  public void setHasSeenSequenceField(final boolean hasSeenSequenceField) {
    this.hasSeenSequenceField = hasSeenSequenceField;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof PositionBasedImportIndexDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(), positionOfLastEntity, sequenceOfLastEntity, hasSeenSequenceField);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final PositionBasedImportIndexDto that = (PositionBasedImportIndexDto) o;
    return positionOfLastEntity == that.positionOfLastEntity
        && sequenceOfLastEntity == that.sequenceOfLastEntity
        && hasSeenSequenceField == that.hasSeenSequenceField;
  }

  @Override
  public String toString() {
    return "PositionBasedImportIndexDto(positionOfLastEntity="
        + getPositionOfLastEntity()
        + ", sequenceOfLastEntity="
        + getSequenceOfLastEntity()
        + ", dbTypeIndexRefersTo="
        + getDbTypeIndexRefersTo()
        + ", hasSeenSequenceField="
        + isHasSeenSequenceField()
        + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String positionOfLastEntity = "positionOfLastEntity";
    public static final String sequenceOfLastEntity = "sequenceOfLastEntity";
    // With the support of Opensearch it was decided to get rid of ES prefixes in variable names.
    // However, to avoid reindexing it was decided to keep original field names in indices
    public static final String dbTypeIndexRefersTo = "esTypeIndexRefersTo";
    public static final String hasSeenSequenceField = "hasSeenSequenceField";
  }
}
