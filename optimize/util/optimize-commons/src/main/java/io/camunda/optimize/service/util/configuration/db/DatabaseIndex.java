/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

public class DatabaseIndex {

  @JsonProperty("number_of_replicas")
  private Integer numberOfReplicas;

  @JsonProperty("number_of_shards")
  private Integer numberOfShards;

  @JsonProperty("refresh_interval")
  private String refreshInterval;

  @JsonProperty("nested_documents_limit")
  private Integer nestedDocumentsLimit;

  private String prefix;

  public DatabaseIndex() {}

  public Integer getNumberOfReplicas() {
    return numberOfReplicas;
  }

  @JsonProperty("number_of_replicas")
  public void setNumberOfReplicas(final Integer numberOfReplicas) {
    this.numberOfReplicas = numberOfReplicas;
  }

  public Integer getNumberOfShards() {
    return numberOfShards;
  }

  @JsonProperty("number_of_shards")
  public void setNumberOfShards(final Integer numberOfShards) {
    this.numberOfShards = numberOfShards;
  }

  public String getRefreshInterval() {
    return refreshInterval;
  }

  @JsonProperty("refresh_interval")
  public void setRefreshInterval(final String refreshInterval) {
    this.refreshInterval = refreshInterval;
  }

  public Integer getNestedDocumentsLimit() {
    return nestedDocumentsLimit;
  }

  @JsonProperty("nested_documents_limit")
  public void setNestedDocumentsLimit(final Integer nestedDocumentsLimit) {
    this.nestedDocumentsLimit = nestedDocumentsLimit;
  }

  public String getPrefix() {
    return prefix;
  }

  public void setPrefix(final String prefix) {
    this.prefix = prefix;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof DatabaseIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        prefix, numberOfShards, numberOfReplicas, refreshInterval, nestedDocumentsLimit);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final DatabaseIndex that = (DatabaseIndex) o;
    return Objects.equals(numberOfShards, that.numberOfShards)
        && Objects.equals(numberOfReplicas, that.numberOfReplicas)
        && Objects.equals(nestedDocumentsLimit, that.nestedDocumentsLimit)
        && Objects.equals(prefix, that.prefix)
        && Objects.equals(refreshInterval, that.refreshInterval);
  }

  @Override
  public String toString() {
    return "DatabaseIndex(numberOfReplicas="
        + getNumberOfReplicas()
        + ", numberOfShards="
        + getNumberOfShards()
        + ", refreshInterval="
        + getRefreshInterval()
        + ", nestedDocumentsLimit="
        + getNestedDocumentsLimit()
        + ", prefix="
        + getPrefix()
        + ")";
  }
}
