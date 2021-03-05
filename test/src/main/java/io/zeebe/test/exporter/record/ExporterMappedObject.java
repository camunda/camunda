/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.test.exporter.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.broker.exporter.ExporterObjectMapper;

class ExporterMappedObject {

  protected static final ExporterObjectMapper OBJECT_MAPPER = new ExporterObjectMapper();

  @JsonIgnore private String json;

  public String toJson() {
    if (json != null) {
      return json;
    }

    return OBJECT_MAPPER.toJson(this);
  }

  public ExporterMappedObject setJson(final String json) {
    this.json = json;
    return this;
  }

  public ExporterMappedObject setJson(final Object object) {
    json = OBJECT_MAPPER.toJson(object);
    return this;
  }
}
