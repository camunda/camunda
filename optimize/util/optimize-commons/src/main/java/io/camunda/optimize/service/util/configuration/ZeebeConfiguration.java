/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ZeebeConfiguration {

  private boolean enabled;
  private String name;
  private int partitionCount;
  private int maxImportPageSize;
  private boolean includeObjectVariableValue;
  private boolean variableImportEnabled;
  private ZeebeImportConfiguration importConfig;
}
