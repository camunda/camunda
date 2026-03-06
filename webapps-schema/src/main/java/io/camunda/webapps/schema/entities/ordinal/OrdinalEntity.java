/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.ordinal;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.webapps.schema.entities.PartitionedEntity;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Represents an exported ordinal tick. Each document captures the ordinal value (monotonically
 * incrementing minute counter) and the wall-clock date/time at which the ordinal was advanced.
 */
public final class OrdinalEntity
    implements ExporterEntity<OrdinalEntity>, PartitionedEntity<OrdinalEntity> {

  private String id;
  private int ordinal;
  private OffsetDateTime dateTime;
  private int partitionId;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public OrdinalEntity setId(final String id) {
    this.id = id;
    return this;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public OrdinalEntity setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
    return this;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public OrdinalEntity setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
    return this;
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public OrdinalEntity setPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, ordinal, dateTime, partitionId);
  }

  @Override
  public boolean equals(final Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    final var that = (OrdinalEntity) obj;
    return Objects.equals(id, that.id)
        && ordinal == that.ordinal
        && Objects.equals(dateTime, that.dateTime)
        && partitionId == that.partitionId;
  }

  @Override
  public String toString() {
    return "OrdinalEntity[id=%s, ordinal=%d, dateTime=%s, partitionId=%d]"
        .formatted(id, ordinal, dateTime, partitionId);
  }
}
