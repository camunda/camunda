/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.page;

public class PositionBasedImportPage implements ImportPage {

  private Long position = 0L;
  private Long sequence = 0L;
  private boolean hasSeenSequenceField = false;

  public PositionBasedImportPage() {}

  public Long getPosition() {
    return position;
  }

  public void setPosition(final Long position) {
    this.position = position;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(final Long sequence) {
    this.sequence = sequence;
  }

  public boolean isHasSeenSequenceField() {
    return hasSeenSequenceField;
  }

  public void setHasSeenSequenceField(final boolean hasSeenSequenceField) {
    this.hasSeenSequenceField = hasSeenSequenceField;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PositionBasedImportPage;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $position = getPosition();
    result = result * PRIME + ($position == null ? 43 : $position.hashCode());
    final Object $sequence = getSequence();
    result = result * PRIME + ($sequence == null ? 43 : $sequence.hashCode());
    result = result * PRIME + (isHasSeenSequenceField() ? 79 : 97);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof PositionBasedImportPage)) {
      return false;
    }
    final PositionBasedImportPage other = (PositionBasedImportPage) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$position = getPosition();
    final Object other$position = other.getPosition();
    if (this$position == null ? other$position != null : !this$position.equals(other$position)) {
      return false;
    }
    final Object this$sequence = getSequence();
    final Object other$sequence = other.getSequence();
    if (this$sequence == null ? other$sequence != null : !this$sequence.equals(other$sequence)) {
      return false;
    }
    if (isHasSeenSequenceField() != other.isHasSeenSequenceField()) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "PositionBasedImportPage(position="
        + getPosition()
        + ", sequence="
        + getSequence()
        + ", hasSeenSequenceField="
        + isHasSeenSequenceField()
        + ")";
  }
}
