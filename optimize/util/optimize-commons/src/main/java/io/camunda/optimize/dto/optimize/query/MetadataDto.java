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
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $schemaVersion = getSchemaVersion();
    result = result * PRIME + ($schemaVersion == null ? 43 : $schemaVersion.hashCode());
    final Object $installationId = getInstallationId();
    result = result * PRIME + ($installationId == null ? 43 : $installationId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MetadataDto)) {
      return false;
    }
    final MetadataDto other = (MetadataDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$schemaVersion = getSchemaVersion();
    final Object other$schemaVersion = other.getSchemaVersion();
    if (this$schemaVersion == null
        ? other$schemaVersion != null
        : !this$schemaVersion.equals(other$schemaVersion)) {
      return false;
    }
    final Object this$installationId = getInstallationId();
    final Object other$installationId = other.getInstallationId();
    if (this$installationId == null
        ? other$installationId != null
        : !this$installationId.equals(other$installationId)) {
      return false;
    }
    return true;
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
