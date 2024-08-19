/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.db;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    final int PRIME = 59;
    int result = 1;
    final Object $numberOfReplicas = getNumberOfReplicas();
    result = result * PRIME + ($numberOfReplicas == null ? 43 : $numberOfReplicas.hashCode());
    final Object $numberOfShards = getNumberOfShards();
    result = result * PRIME + ($numberOfShards == null ? 43 : $numberOfShards.hashCode());
    final Object $refreshInterval = getRefreshInterval();
    result = result * PRIME + ($refreshInterval == null ? 43 : $refreshInterval.hashCode());
    final Object $nestedDocumentsLimit = getNestedDocumentsLimit();
    result =
        result * PRIME + ($nestedDocumentsLimit == null ? 43 : $nestedDocumentsLimit.hashCode());
    final Object $prefix = getPrefix();
    result = result * PRIME + ($prefix == null ? 43 : $prefix.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof DatabaseIndex)) {
      return false;
    }
    final DatabaseIndex other = (DatabaseIndex) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$numberOfReplicas = getNumberOfReplicas();
    final Object other$numberOfReplicas = other.getNumberOfReplicas();
    if (this$numberOfReplicas == null
        ? other$numberOfReplicas != null
        : !this$numberOfReplicas.equals(other$numberOfReplicas)) {
      return false;
    }
    final Object this$numberOfShards = getNumberOfShards();
    final Object other$numberOfShards = other.getNumberOfShards();
    if (this$numberOfShards == null
        ? other$numberOfShards != null
        : !this$numberOfShards.equals(other$numberOfShards)) {
      return false;
    }
    final Object this$refreshInterval = getRefreshInterval();
    final Object other$refreshInterval = other.getRefreshInterval();
    if (this$refreshInterval == null
        ? other$refreshInterval != null
        : !this$refreshInterval.equals(other$refreshInterval)) {
      return false;
    }
    final Object this$nestedDocumentsLimit = getNestedDocumentsLimit();
    final Object other$nestedDocumentsLimit = other.getNestedDocumentsLimit();
    if (this$nestedDocumentsLimit == null
        ? other$nestedDocumentsLimit != null
        : !this$nestedDocumentsLimit.equals(other$nestedDocumentsLimit)) {
      return false;
    }
    final Object this$prefix = getPrefix();
    final Object other$prefix = other.getPrefix();
    if (this$prefix == null ? other$prefix != null : !this$prefix.equals(other$prefix)) {
      return false;
    }
    return true;
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
