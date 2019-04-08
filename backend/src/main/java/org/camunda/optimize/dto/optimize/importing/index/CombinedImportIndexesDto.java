/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing.index;

import java.util.ArrayList;
import java.util.List;

public class CombinedImportIndexesDto {

  private List<TimestampBasedImportIndexDto> definitionBasedIndexes = new ArrayList<>();
  private List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

  public List<TimestampBasedImportIndexDto> getDefinitionBasedIndexes() {
    return definitionBasedIndexes;
  }

  public List<AllEntitiesBasedImportIndexDto> getAllEntitiesBasedImportIndexes() {
    return allEntitiesBasedImportIndexes;
  }

  public void addDefinitionBasedIndexDto(TimestampBasedImportIndexDto timestampBasedImportIndexDto) {
    definitionBasedIndexes.add(timestampBasedImportIndexDto);
  }

  public void addAllEntitiesBasedIndexDto(AllEntitiesBasedImportIndexDto importIndexDto) {
    allEntitiesBasedImportIndexes.add(importIndexDto);
  }

  public void setDefinitionBasedIndexes(List<TimestampBasedImportIndexDto> definitionBasedIndexes) {
    this.definitionBasedIndexes = definitionBasedIndexes;
  }

  public void setAllEntitiesBasedImportIndexes(List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes) {
    this.allEntitiesBasedImportIndexes = allEntitiesBasedImportIndexes;
  }
}
