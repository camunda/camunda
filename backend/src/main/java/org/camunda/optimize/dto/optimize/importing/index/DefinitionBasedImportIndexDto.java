package org.camunda.optimize.dto.optimize.importing.index;

import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;

import java.util.LinkedList;
import java.util.Queue;

public class DefinitionBasedImportIndexDto implements ImportIndexDto {

  protected DefinitionBasedImportPage currentProcessDefinition;
  protected LinkedList<DefinitionBasedImportPage> processDefinitionsToImport;
  protected String esTypeIndexRefersTo;
  protected String engine;

  public DefinitionBasedImportPage getCurrentProcessDefinition() {
    return currentProcessDefinition;
  }

  public void setCurrentProcessDefinition(DefinitionBasedImportPage currentProcessDefinition) {
    this.currentProcessDefinition = currentProcessDefinition;
  }

  public LinkedList<DefinitionBasedImportPage> getProcessDefinitionsToImport() {
    return processDefinitionsToImport;
  }

  public void setProcessDefinitionsToImport(LinkedList<DefinitionBasedImportPage> processDefinitionsToImport) {
    this.processDefinitionsToImport = processDefinitionsToImport;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }
}
