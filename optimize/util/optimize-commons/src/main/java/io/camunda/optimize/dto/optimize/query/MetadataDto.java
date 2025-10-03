/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import java.io.Serializable;
import java.util.Objects;

public class MetadataDto implements OptimizeDto, Serializable {

  private String schemaVersion;
  private String installationId;

  public MetadataDto(final String schemaVersion, final String installationId) {
    this.schemaVersion = schemaVersion;
    this.installationId = installationId;
  }

  protected MetadataDto() {}

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(final String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getInstallationId() {
    return installationId;
  }

  public void setInstallationId(final String installationId) {
    this.installationId = installationId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MetadataDto;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final MetadataDto that = (MetadataDto) o;
    return Objects.equals(schemaVersion, that.schemaVersion)
        && Objects.equals(installationId, that.installationId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(schemaVersion, installationId);
  }

  @Override
  public String toString() {
    return "MetadataDto(schemaVersion="
        + getSchemaVersion()
        + ", installationId="
        + getInstallationId()
        + ")";
  }

  public enum Fields {
    schemaVersion,
    installationId
  }
}
