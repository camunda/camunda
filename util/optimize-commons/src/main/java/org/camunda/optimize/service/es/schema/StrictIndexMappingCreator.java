/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.camunda.optimize.service.es.schema.DynamicMappingsBuilder.DYNAMIC_MAPPINGS_VALUE_DEFAULT;
import static org.camunda.optimize.service.es.schema.DynamicMappingsBuilder.createDynamicSettings;

public abstract class StrictIndexMappingCreator implements IndexMappingCreator, PropertiesAppender {
  private Logger logger = LoggerFactory.getLogger(StrictIndexMappingCreator.class);

  private String dynamicMappingsValue = DYNAMIC_MAPPINGS_VALUE_DEFAULT;

  @Override
  public XContentBuilder getSource() {
    XContentBuilder source = null;
    try {
      source = createDynamicSettings(this, dynamicMappingsValue);
    } catch (IOException e) {
      String message = "Could not add mapping to the index for type '" + getIndexName() + "' , type '" + getIndexName() + "'!";
      logger.error(message, e);
    }
    return source;
  }

  public String getDynamicMappingsValue() {
    return dynamicMappingsValue;
  }

  public void setDynamicMappingsValue(final String dynamicMappingsValue) {
    this.dynamicMappingsValue = dynamicMappingsValue;
  }
}
