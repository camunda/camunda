/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import io.camunda.zeebe.broker.exporter.ExporterObjectMapper;

public abstract class ExporterMappedObject {

  protected static final ExporterObjectMapper OBJECT_MAPPER = new ExporterObjectMapper();

  public String toJson() {
    return OBJECT_MAPPER.toJson(this);
  }
}
