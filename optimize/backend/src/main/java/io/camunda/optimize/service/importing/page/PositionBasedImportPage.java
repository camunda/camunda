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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
