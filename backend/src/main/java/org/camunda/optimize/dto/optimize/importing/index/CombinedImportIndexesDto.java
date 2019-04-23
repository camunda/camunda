/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.importing.index;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CombinedImportIndexesDto {

  private List<TimestampBasedImportIndexDto> definitionBasedIndexes = new ArrayList<>();
  private List<AllEntitiesBasedImportIndexDto> allEntitiesBasedImportIndexes = new ArrayList<>();

  public void addDefinitionBasedIndexDto(TimestampBasedImportIndexDto timestampBasedImportIndexDto) {
    definitionBasedIndexes.add(timestampBasedImportIndexDto);
  }

  public void addAllEntitiesBasedIndexDto(AllEntitiesBasedImportIndexDto importIndexDto) {
    allEntitiesBasedImportIndexes.add(importIndexDto);
  }
}
