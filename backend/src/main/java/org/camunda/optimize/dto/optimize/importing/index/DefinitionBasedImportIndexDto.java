package org.camunda.optimize.dto.optimize.importing.index;

import org.camunda.optimize.service.engine.importing.index.page.DefinitionBasedImportPage;

import java.util.List;

public class DefinitionBasedImportIndexDto implements ImportIndexDto {

  protected DefinitionBasedImportPage currentProcessDefinition;
  protected long totalEntitiesImported;
  protected List<DefinitionBasedImportPage> alreadyImportedProcessDefinitions;
  protected List<DefinitionBasedImportPage> processDefinitionsToImport;
  protected String esTypeIndexRefersTo;
  protected String engine;

  public DefinitionBasedImportPage getCurrentProcessDefinition() {
    return currentProcessDefinition;
  }

  public void setCurrentProcessDefinition(DefinitionBasedImportPage currentProcessDefinition) {
    this.currentProcessDefinition = currentProcessDefinition;
  }

  public List<DefinitionBasedImportPage> getAlreadyImportedProcessDefinitions() {
    return alreadyImportedProcessDefinitions;
  }

  public void setAlreadyImportedProcessDefinitions(List<DefinitionBasedImportPage>
                                                     alreadyImportedProcessDefinitions) {
    this.alreadyImportedProcessDefinitions = alreadyImportedProcessDefinitions;
  }

  public List<DefinitionBasedImportPage> getProcessDefinitionsToImport() {
    return processDefinitionsToImport;
  }

  public void setProcessDefinitionsToImport(List<DefinitionBasedImportPage> processDefinitionsToImport) {
    this.processDefinitionsToImport = processDefinitionsToImport;
  }

  public long getTotalEntitiesImported() {
    return totalEntitiesImported;
  }

  public void setTotalEntitiesImported(long totalEntitiesImported) {
    this.totalEntitiesImported = totalEntitiesImported;
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
