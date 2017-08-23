package org.camunda.optimize.dto.optimize.importing;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.List;

public class DefinitionBasedImportIndexDto implements OptimizeDto {

  protected int currentDefinitionBasedImportIndex;
  protected int totalEntitiesImported;
  protected String currentProcessDefinitionId;
  protected List<DefinitionImportInformation> alreadyImportedProcessDefinitions;
  protected String engine;

  public int getCurrentDefinitionBasedImportIndex() {
    return currentDefinitionBasedImportIndex;
  }

  public void setCurrentDefinitionBasedImportIndex(int currentDefinitionBasedImportIndex) {
    this.currentDefinitionBasedImportIndex = currentDefinitionBasedImportIndex;
  }

  public String getCurrentProcessDefinitionId() {
    return currentProcessDefinitionId;
  }

  public void setCurrentProcessDefinitionId(String currentProcessDefinitionId) {
    this.currentProcessDefinitionId = currentProcessDefinitionId;
  }

  public DefinitionImportInformation createCurrentProcessDefinition() {
    DefinitionImportInformation currentDefinition = new DefinitionImportInformation();
    currentDefinition.setProcessDefinitionId(currentProcessDefinitionId);
    currentDefinition.setDefinitionBasedImportIndex(currentDefinitionBasedImportIndex);
    return currentDefinition;
  }

  public List<DefinitionImportInformation> getAlreadyImportedProcessDefinitions() {
    return alreadyImportedProcessDefinitions;
  }

  public void setAlreadyImportedProcessDefinitions(List<DefinitionImportInformation>
                                                     alreadyImportedProcessDefinitions) {
    this.alreadyImportedProcessDefinitions = alreadyImportedProcessDefinitions;
  }

  public int getTotalEntitiesImported() {
    return totalEntitiesImported;
  }

  public void setTotalEntitiesImported(int totalEntitiesImported) {
    this.totalEntitiesImported = totalEntitiesImported;
  }

  public String getEngine() {
    return engine;
  }

  public void setEngine(String engine) {
    this.engine = engine;
  }
}
