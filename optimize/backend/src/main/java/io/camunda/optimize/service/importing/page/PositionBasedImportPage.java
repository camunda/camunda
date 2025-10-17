/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.page;

import java.util.Objects;

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
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final PositionBasedImportPage that = (PositionBasedImportPage) o;
    return hasSeenSequenceField == that.hasSeenSequenceField
        && Objects.equals(position, that.position)
        && Objects.equals(sequence, that.sequence);
  }

  @Override
  public int hashCode() {
    return Objects.hash(position, sequence, hasSeenSequenceField);
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
