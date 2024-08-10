/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import java.time.OffsetDateTime;
import java.util.Objects;

public abstract class AbstractStep implements Step {

  private String content = null;
  private String description = null;
  private OffsetDateTime createdDate;
  private OffsetDateTime appliedDate;
  private String indexName = null;
  private boolean isApplied = false;
  private String version = null;
  private final Integer order = 0;

  @Override
  public OffsetDateTime getCreatedDate() {
    if (createdDate == null) {
      createdDate = OffsetDateTime.now();
    }
    return createdDate;
  }

  @Override
  public Step setCreatedDate(final OffsetDateTime createDate) {
    createdDate = createDate;
    return this;
  }

  @Override
  public OffsetDateTime getAppliedDate() {
    return appliedDate;
  }

  @Override
  public Step setAppliedDate(final OffsetDateTime appliedDate) {
    this.appliedDate = appliedDate;
    return this;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public Integer getOrder() {
    return order;
  }

  @Override
  public boolean isApplied() {
    return isApplied;
  }

  @Override
  public Step setApplied(final boolean isApplied) {
    this.isApplied = isApplied;
    return this;
  }

  @Override
  public String getIndexName() {
    return indexName;
  }

  @Override
  public String getContent() {
    return content;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public void setContent(final String content) {
    this.content = content;
  }

  public void setIndexName(final String indexName) {
    this.indexName = indexName;
  }

  public void setVersion(final String version) {
    this.version = version;
  }

  @Override
  public int hashCode() {
    return Objects.hash(indexName, version, order);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final AbstractStep that = (AbstractStep) o;
    return Objects.equals(indexName, that.indexName)
        && Objects.equals(version, that.version)
        && Objects.equals(order, that.order);
  }

  @Override
  public String toString() {
    return "AbstractStep{"
        + "content='"
        + content
        + '\''
        + ", description='"
        + description
        + '\''
        + ", createdDate="
        + createdDate
        + ", appliedDate="
        + appliedDate
        + ", indexName='"
        + indexName
        + '\''
        + ", isApplied="
        + isApplied
        + ", version='"
        + version
        + '\''
        + ", order="
        + order
        + '}';
  }
}
