/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.management;

import org.camunda.operate.schema.IndexSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ElsIndicesCheck {

  private static Logger logger = LoggerFactory.getLogger(ElsIndicesCheck.class);

  @Autowired
  private IndexSchemaValidator indexSchemaValidator;

  public boolean indicesArePresent() {
    return indexSchemaValidator.schemaExists();
  }

}
