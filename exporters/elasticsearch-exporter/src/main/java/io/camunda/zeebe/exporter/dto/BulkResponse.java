/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BulkResponse(boolean errors, List<Item> items) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(Result index, Result update) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Result(int status, Error error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Error(String type, String reason) {}
}
