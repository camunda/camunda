/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET}. */
public final class DatasetRow {

  private long cubeId;
  private String name;
  private String sourceFact;
  private String kind;
  private String keyField;
  private long latenessMs;
  private int schemaVersion;

  public DatasetRow() {}

  public DatasetRow(
      final long cubeId,
      final String name,
      final String sourceFact,
      final String kind,
      final String keyField,
      final long latenessMs,
      final int schemaVersion) {
    this.cubeId = cubeId;
    this.name = name;
    this.sourceFact = sourceFact;
    this.kind = kind;
    this.keyField = keyField;
    this.latenessMs = latenessMs;
    this.schemaVersion = schemaVersion;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getSourceFact() {
    return sourceFact;
  }

  public void setSourceFact(final String sourceFact) {
    this.sourceFact = sourceFact;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(final String kind) {
    this.kind = kind;
  }

  public String getKeyField() {
    return keyField;
  }

  public void setKeyField(final String keyField) {
    this.keyField = keyField;
  }

  public long getLatenessMs() {
    return latenessMs;
  }

  public void setLatenessMs(final long latenessMs) {
    this.latenessMs = latenessMs;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(final int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
}
