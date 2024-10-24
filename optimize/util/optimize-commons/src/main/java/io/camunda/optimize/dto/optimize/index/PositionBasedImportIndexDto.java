/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;

public class PositionBasedImportIndexDto extends ImportIndexDto<ZeebeDataSourceDto> {

  protected long positionOfLastEntity = 0;
  protected long sequenceOfLastEntity = 0;
  protected String esTypeIndexRefersTo;
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

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(final String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  public boolean isHasSeenSequenceField() {
    return hasSeenSequenceField;
  }

  public void setHasSeenSequenceField(final boolean hasSeenSequenceField) {
    this.hasSeenSequenceField = hasSeenSequenceField;
  }

  @Override
  public String toString() {
    return "PositionBasedImportIndexDto(positionOfLastEntity="
        + getPositionOfLastEntity()
        + ", sequenceOfLastEntity="
        + getSequenceOfLastEntity()
        + ", esTypeIndexRefersTo="
        + getEsTypeIndexRefersTo()
        + ", hasSeenSequenceField="
        + isHasSeenSequenceField()
        + ")";
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof PositionBasedImportIndexDto;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String positionOfLastEntity = "positionOfLastEntity";
    public static final String sequenceOfLastEntity = "sequenceOfLastEntity";
    public static final String esTypeIndexRefersTo = "esTypeIndexRefersTo";
    public static final String hasSeenSequenceField = "hasSeenSequenceField";
  }
}
