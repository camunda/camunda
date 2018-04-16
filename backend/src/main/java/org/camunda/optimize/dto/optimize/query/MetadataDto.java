package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.io.Serializable;

/**
 * @author Askar Akhmerov
 */
public class MetadataDto implements OptimizeDto, Serializable {

  private String schemaVersion;

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }
}
