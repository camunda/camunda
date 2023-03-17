/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter.opensearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Index template representation. You can read more about index templates <a
 * href="https://opensearch.org/docs/latest/im-plugin/index-templates/">here</a>.
 */
@JsonInclude(Include.NON_EMPTY)
public record Template(
    @JsonProperty("index_patterns") List<String> patterns,
    @JsonProperty("composed_of") List<String> composedOf,
    TemplateProperty template,
    Long priority,
    Long version) {

  @JsonInclude(Include.NON_EMPTY)
  public record TemplateProperty(
      Map<String, Object> aliases, Map<String, Object> settings, Map<String, Object> mappings) {}
}
