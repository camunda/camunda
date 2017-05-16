package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.List;

public class DefinitionBasedImportIndexDto implements OptimizeDto {

  protected int importIndex;
  protected int totalEntitiesImported;
  protected String currentProcessDefinition;
  protected List<String> alreadyImportedProcessDefinitions;

  public int getImportIndex() {
    return importIndex;
  }

  public void setImportIndex(int importIndex) {
    this.importIndex = importIndex;
  }

  public String getCurrentProcessDefinition() {
    return currentProcessDefinition;
  }

  public void setCurrentProcessDefinition(String currentProcessDefinition) {
    this.currentProcessDefinition = currentProcessDefinition;
  }

  public List<String> getAlreadyImportedProcessDefinitions() {
    return alreadyImportedProcessDefinitions;
  }

  public void setAlreadyImportedProcessDefinitions(List<String> alreadyImportedProcessDefinitions) {
    this.alreadyImportedProcessDefinitions = alreadyImportedProcessDefinitions;
  }

  public int getTotalEntitiesImported() {
    return totalEntitiesImported;
  }

  public void setTotalEntitiesImported(int totalEntitiesImported) {
    this.totalEntitiesImported = totalEntitiesImported;
  }
}
