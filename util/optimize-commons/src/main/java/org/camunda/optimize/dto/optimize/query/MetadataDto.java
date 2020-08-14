/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query;

import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

@FieldNameConstants(asEnum = true)
public class MetadataDto implements OptimizeDto, Serializable {

  private String schemaVersion;
  private String installationId;

  protected MetadataDto() {
  }

  public MetadataDto(final String schemaVersion,
                     final String installationId) {
    this.schemaVersion = schemaVersion;
    this.installationId = installationId;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public String getInstallationId() {
    return installationId;
  }

  public void setInstallationId(String installationId) {
    this.installationId = installationId;
  }
}
