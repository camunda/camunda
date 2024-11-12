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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
