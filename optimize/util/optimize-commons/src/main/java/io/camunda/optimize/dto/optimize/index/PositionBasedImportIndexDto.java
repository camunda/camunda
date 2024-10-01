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
    if (o == this) {
      return true;
    }
    if (!(o instanceof PositionBasedImportIndexDto)) {
      return false;
    }
    final PositionBasedImportIndexDto other = (PositionBasedImportIndexDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (getPositionOfLastEntity() != other.getPositionOfLastEntity()) {
      return false;
    }
    if (getSequenceOfLastEntity() != other.getSequenceOfLastEntity()) {
      return false;
    }
    final Object this$esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    final Object other$esTypeIndexRefersTo = other.getEsTypeIndexRefersTo();
    if (this$esTypeIndexRefersTo == null
        ? other$esTypeIndexRefersTo != null
        : !this$esTypeIndexRefersTo.equals(other$esTypeIndexRefersTo)) {
      return false;
    }
    if (isHasSeenSequenceField() != other.isHasSeenSequenceField()) {
      return false;
    }
    return true;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof PositionBasedImportIndexDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final long $positionOfLastEntity = getPositionOfLastEntity();
    result = result * PRIME + (int) ($positionOfLastEntity >>> 32 ^ $positionOfLastEntity);
    final long $sequenceOfLastEntity = getSequenceOfLastEntity();
    result = result * PRIME + (int) ($sequenceOfLastEntity >>> 32 ^ $sequenceOfLastEntity);
    final Object $esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    result = result * PRIME + ($esTypeIndexRefersTo == null ? 43 : $esTypeIndexRefersTo.hashCode());
    result = result * PRIME + (isHasSeenSequenceField() ? 79 : 97);
    return result;
  }

  public static final class Fields {

    public static final String positionOfLastEntity = "positionOfLastEntity";
    public static final String sequenceOfLastEntity = "sequenceOfLastEntity";
    public static final String esTypeIndexRefersTo = "esTypeIndexRefersTo";
    public static final String hasSeenSequenceField = "hasSeenSequenceField";
  }
}
