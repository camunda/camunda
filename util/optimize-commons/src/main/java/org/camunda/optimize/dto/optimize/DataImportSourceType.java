/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonValue;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_DATA_SOURCE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENTS_DATA_SOURCE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INGESTED_DATA_SOURCE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_DATA_SOURCE;

public enum DataImportSourceType {
  ENGINE(ENGINE_DATA_SOURCE),
  ZEEBE(ZEEBE_DATA_SOURCE),
  EVENTS(EVENTS_DATA_SOURCE),
  INGESTED_DATA(INGESTED_DATA_SOURCE),
  ;

  private final String id;

  DataImportSourceType(final String id) {
    this.id = id;
  }

  @JsonValue
  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return getId();
  }

}
