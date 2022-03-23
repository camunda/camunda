/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.definition;

import lombok.Data;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class DecisionDefinitionGroupOptimizeDto {

  private String key;
  private List<DecisionDefinitionOptimizeDto> versions = new ArrayList<>();

  public void sort() {
    versions.sort(
      Comparator.comparing(
        DecisionDefinitionOptimizeDto::getVersion, Comparator.comparing(Long::valueOf)
      ).reversed()
    );
  }
}
