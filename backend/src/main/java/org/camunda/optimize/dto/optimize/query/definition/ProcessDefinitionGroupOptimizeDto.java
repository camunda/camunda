/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.Data;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class ProcessDefinitionGroupOptimizeDto extends KeyDefinitionOptimizeDto implements Serializable, OptimizeDto {

  protected List<ProcessDefinitionOptimizeDto> versions = new ArrayList<>();

  public void sort() {
    versions.sort(
      Comparator.comparing(
        ProcessDefinitionOptimizeDto::getVersion, Comparator.comparing(Long::valueOf)
      )
        .reversed()
    );
  }
}
