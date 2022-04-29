/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BulkRequestAction(@JsonProperty(value = "index", required = true) IndexAction index) {
  public record IndexAction(
      @JsonProperty("_index") String index, @JsonProperty("_id") String id, String routing) {}
}
