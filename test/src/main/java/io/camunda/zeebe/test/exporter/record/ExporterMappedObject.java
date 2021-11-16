/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.exporter.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.broker.exporter.ExporterObjectMapper;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class ExporterMappedObject {

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
