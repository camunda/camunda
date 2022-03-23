/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.variable;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
public class ProcessVariableSourceDto {

  private String processInstanceId;
  private String processDefinitionKey;
  @Builder.Default
  private List<String> processDefinitionVersions = new ArrayList<>();
  @Builder.Default
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));

}
