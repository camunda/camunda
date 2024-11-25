/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;

public class PositionBasedImportIndexDto extends ImportIndexDto<ZeebeDataSourceDto> {

  protected long positionOfLastEntity = 0;
  protected long sequenceOfLastEntity = 0;
  protected String dbTypeIndexRefersTo;
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

  @JsonProperty("esTypeIndexRefersTo")
  public String getDbTypeIndexRefersTo() {
    return dbTypeIndexRefersTo;
  }

  public void setDbTypeIndexRefersTo(final String dbTypeIndexRefersTo) {
    this.dbTypeIndexRefersTo = dbTypeIndexRefersTo;
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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
