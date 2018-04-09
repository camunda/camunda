package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;

/**
 * @author Askar Akhmerov
 */
public class VersionedDefinitionBasedImportPage extends DefinitionBasedImportPage {
  protected int version;

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }
}
