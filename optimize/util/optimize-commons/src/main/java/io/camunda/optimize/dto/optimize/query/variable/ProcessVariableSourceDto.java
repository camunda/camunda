/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.variable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessVariableSourceDto {

  private String processInstanceId;
  private String processDefinitionKey;
  @Builder.Default private List<String> processDefinitionVersions = new ArrayList<>();

  @Builder.Default
  private List<String> tenantIds = new ArrayList<>(Collections.singletonList(null));
}
