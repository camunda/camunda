/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_DIMENSION}. */
public final class DimensionRow {

  private long cubeId;
  private int ordinal;
  private String name;
  private String dimType;
  private String enrichment;

  public DimensionRow() {}

  public DimensionRow(
      final long cubeId,
      final int ordinal,
      final String name,
      final String dimType,
      final String enrichment) {
    this.cubeId = cubeId;
    this.ordinal = ordinal;
    this.name = name;
    this.dimType = dimType;
    this.enrichment = enrichment;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getDimType() {
    return dimType;
  }

  public void setDimType(final String dimType) {
    this.dimType = dimType;
  }

  public String getEnrichment() {
    return enrichment;
  }

  public void setEnrichment(final String enrichment) {
    this.enrichment = enrichment;
  }
}
