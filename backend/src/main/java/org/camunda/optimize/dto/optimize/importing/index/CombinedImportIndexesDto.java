package org.camunda.optimize.dto.optimize.importing.index;

import java.util.ArrayList;
import java.util.List;

public class CombinedImportIndexesDto {

  private List<DefinitionBasedImportIndexDto> definitionBasedIndexes = new ArrayList<>();
  private List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

  public List<DefinitionBasedImportIndexDto> getDefinitionBasedIndexes() {
    return definitionBasedIndexes;
  }

  public List<AllEntitiesBasedImportIndexDto> getAllEntitiesBasedImportIndexes() {
    return allEntitiesBasedImportIndexes;
  }

  public void addDefinitionBasedIndexDto(DefinitionBasedImportIndexDto definitionBasedImportIndexDto) {
    definitionBasedIndexes.add(definitionBasedImportIndexDto);
  }

  public void addAllEntitiesBasedIndexDto(AllEntitiesBasedImportIndexDto importIndexDto) {
    allEntitiesBasedImportIndexes.add(importIndexDto);
  }

  public void setDefinitionBasedIndexes(List<DefinitionBasedImportIndexDto> definitionBasedIndexes) {
    this.definitionBasedIndexes = definitionBasedIndexes;
  }

  public void setAllEntitiesBasedImportIndexes(List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes) {
    this.allEntitiesBasedImportIndexes = allEntitiesBasedImportIndexes;
  }
}
