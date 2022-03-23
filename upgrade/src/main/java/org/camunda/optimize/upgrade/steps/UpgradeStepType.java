/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.steps;

import com.fasterxml.jackson.annotation.JsonValue;

public enum UpgradeStepType {
  DATA_INSERT,
  DATA_UPDATE,
  DATA_DELETE,
  SCHEMA_CREATE_INDEX,
  SCHEMA_UPDATE_INDEX,
  SCHEMA_UPDATE_MAPPING,
  SCHEMA_DELETE_INDEX,
  REINDEX,
  ADD_ALIAS
  ;

  @JsonValue
  public String getId() {
    return name().toLowerCase().replace("_", "-");
  }

  @Override
  public String toString() {
    return getId();
  }

}
