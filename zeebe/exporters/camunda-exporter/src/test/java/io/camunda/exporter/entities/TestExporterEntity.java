/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.entities;

import io.camunda.webapps.schema.entities.ExporterEntity;

public class TestExporterEntity implements ExporterEntity<TestExporterEntity> {

  public static final String DEFAULT_TENANT_IDENTIFIER = "<default>";
  private String id;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public TestExporterEntity setId(final String id) {
    this.id = id;
    return this;
  }
}
