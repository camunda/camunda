/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.store;

import io.camunda.webapps.schema.entities.ExporterEntity;
import io.camunda.zeebe.protocol.record.Record;

public class DefaultIndexLocatorProvider implements IndexLocatorProvider {
  private final DefaultIndexLocator defaultIndexLocator = new DefaultIndexLocator();

  @Override
  public IndexLocator createIndexLocator(final Record<?> record) {
    return defaultIndexLocator;
  }

  static class DefaultIndexLocator implements IndexLocator {
    @Override
    public String getIndexLocation(final ExporterEntity<?> entity, final String baseIndexName) {
      return baseIndexName;
    }
  }
}
