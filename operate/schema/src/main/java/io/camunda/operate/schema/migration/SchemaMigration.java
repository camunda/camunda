/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SchemaMigration {

  private static final Logger LOGGER = LoggerFactory.getLogger(SchemaMigration.class);

  public static void main(final String[] args) {
    // For now, keeping the class so that the init container does not
    // crash loop when running the (old) migration app before starting
    // the old importer-archiver.
    LOGGER.info("No Schema Migration anymore.");
  }
}
