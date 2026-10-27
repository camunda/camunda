/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AdHocActivityMetadata(
    String elementId,
    String elementName,
    String documentation,
    Map<String, String> properties,
    List<AdHocActivityParameter> parameters) {

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public record AdHocActivityParameter(
      String name,
      String description,
      String type,
      Map<String, Object> schema,
      Map<String, Object> options) {}
}
