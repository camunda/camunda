package org.camunda.optimize.dto.optimize.importing.index;

import org.camunda.optimize.dto.optimize.importing.DefinitionImportInformation;

import java.util.List;

public class DefinitionBasedImportIndexDto implements ImportIndexDto {

  protected DefinitionImportInformation currentProcessDefinition;
  protected long totalEntitiesImported;
  protected List<DefinitionImportInformation> alreadyImportedProcessDefinitions;
  protected String esTypeIndexRefersTo;
  protected String engine;

  public DefinitionImportInformation getCurrentProcessDefinition() {
    return currentProcessDefinition;
  }

  public void setCurrentProcessDefinition(DefinitionImportInformation currentProcessDefinition) {
    this.currentProcessDefinition = currentProcessDefinition;
  }

  public List<DefinitionImportInformation> getAlreadyImportedProcessDefinitions() {
    return alreadyImportedProcessDefinitions;
  }

  public void setAlreadyImportedProcessDefinitions(List<DefinitionImportInformation>
                                                     alreadyImportedProcessDefinitions) {
    this.alreadyImportedProcessDefinitions = alreadyImportedProcessDefinitions;
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
