/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.service.util.configuration.users.AuthorizedUserType;

public class CsvConfiguration {

  @JsonProperty("limit")
  private Integer exportCsvLimit;

  @JsonProperty("delimiter")
  private Character exportCsvDelimiter;

  @JsonProperty("authorizedUsers")
  private AuthorizedUserType authorizedUserType;

  public CsvConfiguration() {}

  public Integer getExportCsvLimit() {
    return exportCsvLimit;
  }

  @JsonProperty("limit")
  public void setExportCsvLimit(final Integer exportCsvLimit) {
    this.exportCsvLimit = exportCsvLimit;
  }

  public Character getExportCsvDelimiter() {
    return exportCsvDelimiter;
  }

  @JsonProperty("delimiter")
  public void setExportCsvDelimiter(final Character exportCsvDelimiter) {
    this.exportCsvDelimiter = exportCsvDelimiter;
  }

  public AuthorizedUserType getAuthorizedUserType() {
    return authorizedUserType;
  }

  @JsonProperty("authorizedUsers")
  public void setAuthorizedUserType(final AuthorizedUserType authorizedUserType) {
    this.authorizedUserType = authorizedUserType;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CsvConfiguration;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $exportCsvLimit = getExportCsvLimit();
    result = result * PRIME + ($exportCsvLimit == null ? 43 : $exportCsvLimit.hashCode());
    final Object $exportCsvDelimiter = getExportCsvDelimiter();
    result = result * PRIME + ($exportCsvDelimiter == null ? 43 : $exportCsvDelimiter.hashCode());
    final Object $authorizedUserType = getAuthorizedUserType();
    result = result * PRIME + ($authorizedUserType == null ? 43 : $authorizedUserType.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof CsvConfiguration)) {
      return false;
    }
    final CsvConfiguration other = (CsvConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$exportCsvLimit = getExportCsvLimit();
    final Object other$exportCsvLimit = other.getExportCsvLimit();
    if (this$exportCsvLimit == null
        ? other$exportCsvLimit != null
        : !this$exportCsvLimit.equals(other$exportCsvLimit)) {
      return false;
    }
    final Object this$exportCsvDelimiter = getExportCsvDelimiter();
    final Object other$exportCsvDelimiter = other.getExportCsvDelimiter();
    if (this$exportCsvDelimiter == null
        ? other$exportCsvDelimiter != null
        : !this$exportCsvDelimiter.equals(other$exportCsvDelimiter)) {
      return false;
    }
    final Object this$authorizedUserType = getAuthorizedUserType();
    final Object other$authorizedUserType = other.getAuthorizedUserType();
    if (this$authorizedUserType == null
        ? other$authorizedUserType != null
        : !this$authorizedUserType.equals(other$authorizedUserType)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "CsvConfiguration(exportCsvLimit="
        + getExportCsvLimit()
        + ", exportCsvDelimiter="
        + getExportCsvDelimiter()
        + ", authorizedUserType="
        + getAuthorizedUserType()
        + ")";
  }
}
