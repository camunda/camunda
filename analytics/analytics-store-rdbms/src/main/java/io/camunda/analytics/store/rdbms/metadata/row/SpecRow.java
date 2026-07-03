/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/**
 * Row model for {@code ANALYTICS_DATASET_SPEC}: the searchable scalar columns plus the full spec as
 * a JSON string. The spec is small, config-like, mostly immutable and read wholesale, so it is
 * stored as an opaque document rather than normalized into child tables; only {@code name}/{@code
 * sourceFact}/{@code kind} are searched.
 */
public final class SpecRow {

  private long cubeId;
  private String name;
  private String sourceFact;
  private String kind;
  private String spec;

  public SpecRow() {}

  public SpecRow(
      final long cubeId,
      final String name,
      final String sourceFact,
      final String kind,
      final String spec) {
    this.cubeId = cubeId;
    this.name = name;
    this.sourceFact = sourceFact;
    this.kind = kind;
    this.spec = spec;
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

  public String getSpec() {
    return spec;
  }

  public void setSpec(final String spec) {
    this.spec = spec;
  }
}
