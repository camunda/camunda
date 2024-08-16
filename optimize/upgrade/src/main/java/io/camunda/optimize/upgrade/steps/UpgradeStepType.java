/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.steps;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum UpgradeStepType {
  DATA_INSERT,
  DATA_UPDATE,
  DATA_DELETE,
  SCHEMA_CREATE_INDEX,
  SCHEMA_UPDATE_INDEX,
  SCHEMA_UPDATE_MAPPING,
  SCHEMA_DELETE_INDEX,
  SCHEMA_DELETE_TEMPLATE,
  REINDEX;

  @JsonValue
  public String getId() {
    return name().toLowerCase(Locale.ENGLISH).replace("_", "-");
  }

  @Override
  public String toString() {
    return getId();
  }
}
