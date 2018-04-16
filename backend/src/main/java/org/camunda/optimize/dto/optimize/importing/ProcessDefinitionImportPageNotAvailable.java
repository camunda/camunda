package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;

public class ProcessDefinitionImportPageNotAvailable extends DefinitionBasedImportPage {

  protected String processDefinitionId = "notAvailable";

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }
}
