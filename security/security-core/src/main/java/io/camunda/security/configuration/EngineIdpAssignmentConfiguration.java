/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EngineIdpAssignmentConfiguration {

  private Map<String, List<String>> engineIdpAssignments = new HashMap<>();

  public Map<String, List<String>> getEngineIdpAssignments() {
    return engineIdpAssignments;
  }

  public void setEngineIdpAssignments(final Map<String, List<String>> engineIdpAssignments) {
    this.engineIdpAssignments = engineIdpAssignments;
  }
}
