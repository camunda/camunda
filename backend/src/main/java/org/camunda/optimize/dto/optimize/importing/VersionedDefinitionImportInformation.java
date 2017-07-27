package org.camunda.optimize.dto.optimize.importing;

/**
 * @author Askar Akhmerov
 */
public class VersionedDefinitionImportInformation extends DefinitionImportInformation {
  protected int version;

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }
}
