/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.webapps.schema.entities.ExporterEntity;

public final class DefaultIndexLocator implements IndexLocator {
  public static final IndexLocator INSTANCE = new DefaultIndexLocator();

  private DefaultIndexLocator() {}

  @Override
  public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
    return baseIndexName;
  }
}
